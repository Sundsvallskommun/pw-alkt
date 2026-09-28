package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MESSAGE_TEMPLATE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@ExtendWith(MockitoExtension.class)
class NotifyCustomerWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String TEMPLATE_ID = "alkt.processing-started";
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
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MESSAGE_TEMPLATE)).thenReturn(TEMPLATE_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(customerMessageServiceMock.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE_ID)).thenReturn(sent);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null));
		verify(customerMessageServiceMock).sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE_ID);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void failsWithoutRetryWhenTheStepNamesNoMessage(final String templateId) {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MESSAGE_TEMPLATE)).thenReturn(templateId);
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Step 'external_task_notify_processing_started' has no input parameter 'messageTemplate' naming the message to send");

		verifyNoInteractions(customerMessageServiceMock, failureHandlerMock, processReportServiceMock);
	}
}
