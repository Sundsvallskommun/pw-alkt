package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.InspectionProtocolService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PROTOCOL_FILE_NAME;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PROTOCOL_TEMPLATE;

@ExtendWith(MockitoExtension.class)
class CreateProtocolWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String TEMPLATE = "inspection.external.protocol";
	private static final String FILE_NAME = "Tillsynsprotokoll.pdf";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private InspectionProtocolService inspectionProtocolServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CreateProtocolWorker worker;

	@Test
	void createsTheProtocolWithTheTemplateAndFileNameOfTheStep() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_PROTOCOL_TEMPLATE)).thenReturn(TEMPLATE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_PROTOCOL_FILE_NAME)).thenReturn(FILE_NAME);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_protocol");
		when(inspectionProtocolServiceMock.createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME)).thenReturn("attachment-id");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_create_protocol", null).withLogMessage("Protocol 'attachment-id' found or created"));
		verify(inspectionProtocolServiceMock).createProtocol(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE, FILE_NAME);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
