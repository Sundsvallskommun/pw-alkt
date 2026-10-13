package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.businesslogic.worker.CheckDecisionWorker.PROCESS_VARIABLE_DECISION_OUTCOME;

@ExtendWith(MockitoExtension.class)
class CheckDecisionWorkerTest {

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
	private CheckDecisionWorker worker;

	@Test
	void handsTheOutcomeToTheEngine() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_decision");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(decisionServiceMock.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn("APPROVAL");
		when(decisionServiceMock.isKnownOutcome("alcohol-serving", "APPROVAL")).thenReturn(true);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_check_decision", null)
			.withVariables(Map.of(PROCESS_VARIABLE_DECISION_OUTCOME, "APPROVAL"))
			.withLogMessage("Decision outcome APPROVAL"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	/** Without a decision the step reruns every hour, and a check that found nothing leaves no entry in the log. */
	@Test
	void leavesNoLogEntryWhenThereIsNoDecisionYet() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_decision");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("action-errand");
		when(decisionServiceMock.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn("NONE");
		when(decisionServiceMock.isKnownOutcome("action-errand", "NONE")).thenReturn(true);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result.logMessage()).isNull();
		assertThat(result.variables()).containsExactly(entry(PROCESS_VARIABLE_DECISION_OUTCOME, "NONE"));
	}

	@Test
	void rejectsAnOutcomeOfAnotherProcessAndKeepsWaiting() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_decision");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("action-errand");
		when(decisionServiceMock.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn("APPROVAL");
		when(decisionServiceMock.isKnownOutcome("action-errand", "APPROVAL")).thenReturn(false);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_check_decision", null)
			.withVariables(Map.of(PROCESS_VARIABLE_DECISION_OUTCOME, "NONE"))
			.withRejection("Decision outcome APPROVAL is not an outcome of process action-errand", "APPROVAL"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
