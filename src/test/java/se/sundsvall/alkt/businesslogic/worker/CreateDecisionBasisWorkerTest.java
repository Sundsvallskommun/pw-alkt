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
import se.sundsvall.alkt.service.DecisionBasisService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.businesslogic.worker.CreateDecisionBasisWorker.PROCESS_VARIABLE_DECISION_BASIS_TEMPLATE;

@ExtendWith(MockitoExtension.class)
class CreateDecisionBasisWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "c2b1f9a4-3d1e-4a57-9a0e-6f1e2b7d8c90";
	private static final String TEMPLATE = "tobacco-sales-decision-basis";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private DecisionBasisService decisionBasisServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CreateDecisionBasisWorker worker;

	@Test
	void createsTheDecisionBasisWithTheTemplateOfTheStep() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_DECISION_BASIS_TEMPLATE)).thenReturn(TEMPLATE);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_decision_basis");
		when(decisionBasisServiceMock.createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE)).thenReturn("attachment-id");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_create_decision_basis", null).withLogMessage("Decision basis 'attachment-id' found or created"));
		verify(decisionBasisServiceMock).createDecisionBasis(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, TEMPLATE);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void failsWithoutRetryWhenTheStepHasNoTemplate() {
		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The step has no decision basis template, see the input parameters in the bpmn schema");

		verifyNoInteractions(decisionBasisServiceMock);
	}
}
