package se.sundsvall.alkt.businesslogic.worker;

import java.util.ArrayList;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.Constants;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractTaskWorkerTest {

	private static class Worker extends AbstractTaskWorker { // Test class extending the abstract class under test

		Worker(FailureHandler failureHandler) {
			super(failureHandler);
		}

		@Override
		public void executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
			// Do nothing
		}
	}

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@InjectMocks
	private Worker worker;

	@BeforeEach
	void clearRequestId() {
		// Guard against request id state leaking in from another test on this thread
		for (var i = 0; (i < 10) && (RequestId.get() != null); i++) {
			RequestId.reset();
		}
	}

	@Test
	void getProcessVariables() {
		// Setup
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var errandId = UUID.randomUUID().toString();

		// Mock
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(municipalityId);
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_NAMESPACE)).thenReturn(namespace);
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_ERRAND_ID)).thenReturn(errandId);

		// Act and assert
		assertThat(worker.getMunicipalityId(externalTaskMock)).isEqualTo(municipalityId);
		assertThat(worker.getNamespace(externalTaskMock)).isEqualTo(namespace);
		assertThat(worker.getErrandId(externalTaskMock)).isEqualTo(errandId);
	}

	@Test
	void logging() {
		// Act and assert - logging must never let an exception escape into the task execution
		assertThatNoException().isThrownBy(() -> {
			worker.logInfo("A message with an argument: {}", "argument");
			worker.logException(externalTaskMock, new IllegalStateException("Boom"));
		});
	}

	@Test
	void execute() {
		final var requestId = UUID.randomUUID().toString();

		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(requestId);

		// Mock static RequestId to verify that static method is being called
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			// Act
			worker.execute(externalTaskMock, externalTaskServiceMock);

			// Verify static method
			requestIdMock.verify(() -> RequestId.init(requestId));
			requestIdMock.verify(RequestId::reset);
		}
	}

	/**
	 * RequestId.init() only writes to the MDC when the thread local counter is zero. Without a matching reset() every task
	 * after the first one on a worker thread would keep logging under the request id of that first task.
	 */
	@Test
	void executeSetsRequestIdPerTaskAndClearsItAfterwards() {
		// Arrange
		final var firstRequestId = UUID.randomUUID().toString();
		final var secondRequestId = UUID.randomUUID().toString();
		final var observedRequestIds = new ArrayList<String>();

		final var recordingWorker = new AbstractTaskWorker(failureHandlerMock) {
			@Override
			protected void executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				observedRequestIds.add(RequestId.get());
			}
		};

		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(firstRequestId, secondRequestId);

		// Act - two tasks executed in sequence on the same thread
		recordingWorker.execute(externalTaskMock, externalTaskServiceMock);
		recordingWorker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert
		assertThat(observedRequestIds).containsExactly(firstRequestId, secondRequestId);
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void executeClearsRequestIdWhenBusinessLogicThrows() {
		// Arrange
		final var requestId = UUID.randomUUID().toString();
		final var throwingWorker = new AbstractTaskWorker(failureHandlerMock) {
			@Override
			protected void executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				throw new IllegalStateException("Boom");
			}
		};

		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(requestId);

		// Act
		assertThatThrownBy(() -> throwingWorker.execute(externalTaskMock, externalTaskServiceMock))
			.isInstanceOf(IllegalStateException.class);

		// Assert
		assertThat(RequestId.get()).isNull();
	}
}
