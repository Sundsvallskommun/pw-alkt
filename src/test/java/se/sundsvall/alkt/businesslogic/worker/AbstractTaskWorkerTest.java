package se.sundsvall.alkt.businesslogic.worker;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.Constants;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractTaskWorkerTest {

	private static class Worker extends AbstractTaskWorker { // Test class extending the abstract class under test

		Worker(ProcessReportService processReportService, FailureHandler failureHandler) {
			super(processReportService, failureHandler);
		}

		@Override
		public ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
			return ProcessStateReport.completed();
		}
	}

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@Mock
	private ProcessReportService processReportServiceMock;

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

		final var recordingWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				observedRequestIds.add(RequestId.get());
				return ProcessStateReport.completed();
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
	void executeWritesTheStepsResultVariablesWhenCompleting() {
		final var variables = Map.<String, Object>of("decision", "APPROVED");
		final var variableWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				return ProcessStateReport.completed().withVariables(variables);
			}
		};

		variableWorker.execute(externalTaskMock, externalTaskServiceMock);

		verify(externalTaskServiceMock).complete(externalTaskMock, variables);
	}

	@Test
	void executeCompletesWithAnEmptyMapWhenTheStepReturnsNoVariables() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(externalTaskServiceMock).complete(externalTaskMock, Map.of());
	}

	@Test
	void reportsTheVersionTheStepReadWhenTheStepOnlyReads() {
		final var readOnlyWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				return ProcessStateReport.completed().withErrandVersion(7L);
			}
		};

		readOnlyWorker.execute(externalTaskMock, externalTaskServiceMock);

		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);
		verify(processReportServiceMock, times(2)).reportProcessState(any(), reportCaptor.capture());
		assertThat(reportCaptor.getValue().errandVersion()).isEqualTo(7L);
	}

	@Test
	void executeHandsAFailingStepToTheFailureHandlerAndClearsRequestId() {
		// Arrange
		final var requestId = UUID.randomUUID().toString();
		final var throwingWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				throw new IllegalStateException("Boom");
			}
		};

		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(requestId);

		// Act - the engine is told about the failure, so nothing escapes to the task client
		throwingWorker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert - RUNNING went out before the throw, and it's the only report from execute() itself
		verify(processReportServiceMock, times(1)).reportProcessState(any(), any());
		verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.running(null, null));
		verify(failureHandlerMock).handleException(externalTaskServiceMock, externalTaskMock, "Boom");
		verify(externalTaskServiceMock, never()).complete(any(), any());
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void executeHandsAPreconditionFailedWriteToTheFailureHandler() {
		// Arrange - the shape of the exception a patchErrand call throws when Support Management answers 412
		final var requestId = UUID.randomUUID().toString();
		final var throwingWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				throw new ClientProblem(HttpStatus.BAD_GATEWAY, "Precondition Failed");
			}
		};

		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(requestId);

		// Act
		throwingWorker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert - not caught anywhere between patchErrand and here, so the task is retried rather than completed
		verify(failureHandlerMock).handleException(externalTaskServiceMock, externalTaskMock, "Bad Gateway: Precondition Failed");
		verify(externalTaskServiceMock, never()).complete(any(), any());
	}
}
