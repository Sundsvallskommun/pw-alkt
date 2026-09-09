package se.sundsvall.alkt.service;

import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.Constants;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessReportServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "b82bd8ac-1507-4d9a-958d-369261eecc15";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private ExternalTask externalTaskMock;

	@InjectMocks
	private ProcessReportService processReportService;

	@Test
	void reportsToSupportManagementOnThePathFromTheProcessVariables() {
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(Constants.PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);

		final var report = ProcessStateReport.completed();
		processReportService.reportProcessState(externalTaskMock, report);

		verify(supportManagementIntegrationMock).patchProcessState(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, report);
	}
}
