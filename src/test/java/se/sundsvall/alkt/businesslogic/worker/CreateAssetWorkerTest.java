package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.AssetService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_CERTIFICATE_TEMPLATE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@ExtendWith(MockitoExtension.class)
class CreateAssetWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private AssetService assetServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CreateAssetWorker worker;

	/** A step without the parameter passes no template, and the asset gets no certificate. */
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = "serving-permit-certificate")
	void createsTheAssetWithTheCertificateTemplateOfTheStep(final String certificateTemplate) {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_CERTIFICATE_TEMPLATE)).thenReturn(certificateTemplate);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY_ALCOHOL_SERVING);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_asset");
		when(assetServiceMock.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, certificateTemplate, PERMIT_TYPE_ALCOHOL_SERVING)).thenReturn("asset-id");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_create_asset", null).withLogMessage("Asset 'asset-id' found or created"));
		verify(assetServiceMock).findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, certificateTemplate, PERMIT_TYPE_ALCOHOL_SERVING);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
