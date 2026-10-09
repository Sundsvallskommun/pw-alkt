package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.CustomerMessageService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.businesslogic.worker.NotifyCustomerWorker.PROCESS_VARIABLE_MESSAGE;

@ExtendWith(MockitoExtension.class)
class NotifyCustomerWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String MESSAGE = "processing-started";
	private static final String ACTIVITY_ID = "external_task_notify_processing_started";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private CustomerMessageService customerMessageServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private NotifyCustomerWorker worker;

	@ParameterizedTest
	@ValueSource(booleans = {
		true, false
	})
	void sendsTheMessageNamedByTheStep(final boolean sent) {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MESSAGE)).thenReturn(MESSAGE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(customerMessageServiceMock.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).thenReturn(sent);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null)
			.withLogMessage(sent ? "Message '%s' sent".formatted(MESSAGE) : "Message '%s' already sent, not sent again".formatted(MESSAGE)));
		verify(customerMessageServiceMock).sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void failsWithoutRetryWhenTheStepNamesNoMessage(final String message) {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MESSAGE)).thenReturn(message);
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Step 'external_task_notify_processing_started' has no input parameter 'message' naming the message to send");

		verifyNoInteractions(customerMessageServiceMock, failureHandlerMock, processReportServiceMock);
	}

	@Test
	void reportsTheWaitStateWhenAFailingStepIsSkipped() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_REQUEST_ID)).thenReturn("request-id");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MESSAGE)).thenReturn(MESSAGE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(externalTaskMock.getProcessInstanceId()).thenReturn("instance-id");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTaskMock.getProcessDefinitionId()).thenReturn("definition-id");
		when(customerMessageServiceMock.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).thenThrow(new IllegalStateException("Support Management is down"));
		when(failureHandlerMock.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "IllegalStateException", true)).thenReturn(true);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock).reportWaitState(new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "instance-id", "alcohol-serving", null), "definition-id");
		verify(externalTaskServiceMock, never()).complete(any(ExternalTask.class), any());
	}

	@Test
	void reportsNoWaitStateWhenAFailingStepIsNotSkipped() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(failureHandlerMock.handleSkippableFailure(externalTaskServiceMock, externalTaskMock,
			"Step 'external_task_notify_processing_started' has no input parameter 'message' naming the message to send", false)).thenReturn(false);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock, never()).reportWaitState(any(), any());
		verify(failureHandlerMock, never()).handleIncident(any(), any(), any());
		verifyNoInteractions(customerMessageServiceMock);
	}
}
