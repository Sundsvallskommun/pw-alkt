package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.PermitCertificateService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.CertificateDelivery;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ASSET_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.service.model.CertificateDelivery.Channel.DIGITAL_MAIL;
import static se.sundsvall.alkt.service.model.CertificateDelivery.Channel.SNAIL_MAIL;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES;

@ExtendWith(MockitoExtension.class)
class SendPermitCertificateWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "7a9c2b54-3f0e-4c1a-9d8e-2b6f1a0c4e11";
	private static final String ASSET_ID = "c1f2e3d4-5b6a-4789-8abc-def012345678";
	private static final String MESSAGE_ID = "0b6c1d2e-8f3a-4b5c-9d7e-1a2b3c4d5e6f";
	private static final String ACTIVITY_ID = "external_task_send_permit_certificate";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private PermitCertificateService permitCertificateServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private SendPermitCertificateWorker worker;

	private void mockTask() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ASSET_ID)).thenReturn(ASSET_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(TOBACCO_SALES.getKey());
	}

	@Test
	void certificateSentAsDigitalMailGetsTheDoneTextOfDigitalMail() {
		mockTask();
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(permitCertificateServiceMock.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES, ASSET_ID))
			.thenReturn(new CertificateDelivery(DIGITAL_MAIL, MESSAGE_ID, false));

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null)
			.withLogMessage("Permit certificate sent as DIGITAL_MAIL, message '%s'".formatted(MESSAGE_ID))
			.withDoneVariant("digital-mail"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void certificateSentAsSnailMailGetsTheDoneTextOfSnailMail() {
		mockTask();
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(permitCertificateServiceMock.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES, ASSET_ID))
			.thenReturn(new CertificateDelivery(SNAIL_MAIL, MESSAGE_ID, false));

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null)
			.withLogMessage("Permit certificate sent as SNAIL_MAIL, message '%s'".formatted(MESSAGE_ID))
			.withDoneVariant("snail-mail"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void certificateAlreadySentCompletesWithALogMessageSayingSo() {
		mockTask();
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(permitCertificateServiceMock.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES, ASSET_ID))
			.thenReturn(new CertificateDelivery(SNAIL_MAIL, MESSAGE_ID, true));

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running(ACTIVITY_ID, null)
			.withLogMessage("Permit certificate already sent as SNAIL_MAIL, message '%s', not sent again".formatted(MESSAGE_ID))
			.withDoneVariant("snail-mail"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void failureWithoutRetryBecomesAnIncidentAtOnce() {
		mockTask();
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_REQUEST_ID)).thenReturn("request-id");
		when(permitCertificateServiceMock.sendCertificate(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TOBACCO_SALES, ASSET_ID))
			.thenThrow(new NonRetryableException("Asset '%s' has no permit certificate to send".formatted(ASSET_ID)));

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(failureHandlerMock).handleIncident(externalTaskServiceMock, externalTaskMock, "Asset '%s' has no permit certificate to send".formatted(ASSET_ID));
		verify(failureHandlerMock, never()).handleException(any(), any(), any());
		verify(externalTaskServiceMock, never()).complete(any(ExternalTask.class), any());
	}
}
