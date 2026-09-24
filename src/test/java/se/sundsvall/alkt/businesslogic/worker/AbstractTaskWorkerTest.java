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
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractTaskWorkerTest {

	private static final String DEFINITION_ID = "alcohol-serving:1:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a";

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

		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			// Act
			worker.execute(externalTaskMock, externalTaskServiceMock);

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
	void executeReportsRunningThenWhatTheStepReturnedAndThenCompletesTheTask() {
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(UUID.randomUUID().toString());

		worker.execute(externalTaskMock, externalTaskServiceMock);

		final var inOrder = inOrder(processReportServiceMock, externalTaskServiceMock);
		inOrder.verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.running(null, null));
		inOrder.verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.completed());
		inOrder.verify(externalTaskServiceMock).complete(externalTaskMock, Map.of());
		verifyNoInteractions(failureHandlerMock);
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
	void reportsTheWaitStateTheProcessRanIntoAfterTheStep() {
		final var processInstanceId = UUID.randomUUID().toString();
		final var errandId = UUID.randomUUID().toString();
		when(externalTaskMock.getProcessInstanceId()).thenReturn(processInstanceId);
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_NAMESPACE)).thenReturn("ALKT");
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_ERRAND_ID)).thenReturn(errandId);
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_REQUEST_ID)).thenReturn(UUID.randomUUID().toString());

		runningWorker().execute(externalTaskMock, externalTaskServiceMock);

		final var inOrder = inOrder(externalTaskServiceMock, processReportServiceMock);
		inOrder.verify(externalTaskServiceMock).complete(any(), any());
		inOrder.verify(processReportServiceMock).reportWaitState(new ReportTarget("2281", "ALKT", errandId, processInstanceId, "alcohol-serving", null), DEFINITION_ID);
	}

	/** A process that ended has nothing left to wait for, so asking the engine would be a call for nothing. */
	@Test
	void asksForNoWaitStateAfterATerminalStep() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock, never()).reportWaitState(any(), any());
	}

	/**
	 * The task is completed by then, so a failing wait state report must reach neither the failure handler nor the client.
	 */
	@Test
	void leavesTheCompletedTaskAloneWhenTheWaitStateReportFails() {
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Support Management is down")).when(processReportServiceMock).reportWaitState(any(), any());

		assertThatNoException().isThrownBy(() -> runningWorker().execute(externalTaskMock, externalTaskServiceMock));

		verify(externalTaskServiceMock).complete(any(), any());
		verifyNoInteractions(failureHandlerMock);
	}

	@Test
	void reportsAnUnchangedRunningOnlyOnce() {
		final var workerWithVariables = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				return ProcessStateReport.running(externalTask.getActivityId(), null).withVariables(Map.of("key", "value"));
			}
		};

		workerWithVariables.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock, times(1)).report(any(ExternalTask.class), any());
		verify(externalTaskServiceMock).complete(externalTaskMock, Map.of("key", "value"));
	}

	@Test
	void reportsTheVersionTheStepReadWhenTheStepOnlyReads() {
		final var readOnlyWorker = new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				return ProcessStateReport.running(externalTask.getActivityId(), null).withErrandVersion(7L);
			}
		};

		readOnlyWorker.execute(externalTaskMock, externalTaskServiceMock);

		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);
		verify(processReportServiceMock, times(2)).report(any(ExternalTask.class), reportCaptor.capture());
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
		verify(processReportServiceMock, times(1)).report(any(ExternalTask.class), any());
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.running(null, null));
		verify(failureHandlerMock).handleException(externalTaskServiceMock, externalTaskMock, "Boom");
		verify(externalTaskServiceMock, never()).complete(any(), any());
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void executeHandsAPreconditionFailedWriteToTheFailureHandler() {
		// Arrange - the shape of the exception a reportProcess call throws when Support Management answers 412
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

		// Assert - not caught anywhere between reportProcess and here, so the task is retried rather than completed
		verify(failureHandlerMock).handleException(externalTaskServiceMock, externalTaskMock, "Bad Gateway: Precondition Failed");
		verify(externalTaskServiceMock, never()).complete(any(), any());
	}

	@Test
	void executeStillRunsTheStepWhenTheRunningReportFails() {
		// Arrange - Support Management being unreachable for the RUNNING report must not fail the business task
		doThrow(new IllegalStateException("Support Management down")).when(processReportServiceMock).report(any(ExternalTask.class), any());

		// Act
		worker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert - the step still ran and completed, and the failure handler was never invoked
		verify(externalTaskServiceMock).complete(externalTaskMock, Map.of());
		verify(failureHandlerMock, never()).handleException(any(), any(), any());
	}

	@Test
	void executeStillCompletesWhenTheFinalReportFails() {
		// Arrange - the RUNNING report succeeds, the COMPLETED report fails; the failure must not undo the completion
		doNothing()
			.doThrow(new IllegalStateException("Support Management down"))
			.when(processReportServiceMock).report(any(ExternalTask.class), any());

		// Act
		worker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert
		verify(externalTaskServiceMock).complete(externalTaskMock, Map.of());
		verify(failureHandlerMock, never()).handleException(any(), any(), any());
	}

	@Test
	void executeDoesNotCompleteWhenTheFinalReportGetsAPreconditionFailed() {
		// Arrange - dept44's Feign decoder collapses a 412 from Support Management into this ClientProblem(BAD_GATEWAY, ...)
		doNothing()
			.doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "support-management error: {status=412 Precondition Failed, title=Precondition Failed}"))
			.when(processReportServiceMock).report(any(ExternalTask.class), any());

		// Act
		worker.execute(externalTaskMock, externalTaskServiceMock);

		// Assert
		verify(externalTaskServiceMock, never()).complete(any(), any());
		verify(failureHandlerMock).handleException(externalTaskServiceMock, externalTaskMock,
			"Bad Gateway: support-management error: {status=412 Precondition Failed, title=Precondition Failed}");
	}

	private AbstractTaskWorker runningWorker() {
		return new AbstractTaskWorker(processReportServiceMock, failureHandlerMock) {
			@Override
			protected ProcessStateReport executeBusinessLogic(ExternalTask externalTask, ExternalTaskService externalTaskService) {
				return ProcessStateReport.running("external_task_check_phase", null);
			}
		};
	}
}
