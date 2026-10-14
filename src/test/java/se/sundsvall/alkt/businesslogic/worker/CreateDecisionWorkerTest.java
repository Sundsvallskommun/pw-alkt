package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.DecisionService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.businesslogic.worker.CreateDecisionWorker.PROCESS_VARIABLE_VALID_TO_PARAMETER;
import static se.sundsvall.alkt.service.model.AlktProcess.CATERING_OCCASION;
import static se.sundsvall.alkt.service.model.AlktProcess.LOW_ALCOHOL_BEER_SERVING;

@ExtendWith(MockitoExtension.class)
class CreateDecisionWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private DecisionService decisionServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CreateDecisionWorker worker;

	@Test
	void createsTheDecisionOfTheProcess() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(LOW_ALCOHOL_BEER_SERVING.key());
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_decision");
		when(decisionServiceMock.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SERVING.key(), null)).thenReturn("decision-id");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_create_decision", null).withLogMessage("Decision 'decision-id' completed"));
		verify(decisionServiceMock).approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SERVING.key(), null);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void passesOnTheParameterTheStepNamesForTheLastDay() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_VALID_TO_PARAMETER)).thenReturn("occasionEndDate");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(CATERING_OCCASION.key());
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_decision");
		when(decisionServiceMock.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CATERING_OCCASION.key(), "occasionEndDate")).thenReturn("decision-id");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_create_decision", null).withLogMessage("Decision 'decision-id' completed"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
