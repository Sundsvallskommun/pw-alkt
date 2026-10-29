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
import se.sundsvall.alkt.service.ErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.CaseNumberCheck;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@ExtendWith(MockitoExtension.class)
class CheckCaseNumberWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "4517d939-3bd9-40b1-80cb-a70585a071d9";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private ErrandService errandServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CheckCaseNumberWorker worker;

	@Test
	void anErrandInTimraWithACaseNumberGoesOn() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_case_number");
		when(errandServiceMock.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(CaseNumberCheck.PRESENT);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_check_case_number", null)
			.withVariables(Map.of("caseNumberAccepted", true))
			.withLogMessage("Case number checked"));
		assertThat(result.rejected()).isFalse();
		verify(errandServiceMock).checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(errandServiceMock);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock, externalTaskServiceMock);
	}

	@Test
	void anErrandInSundsvallGoesOnWithoutAnEntryInTheActivityLog() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_case_number");
		when(errandServiceMock.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(CaseNumberCheck.NOT_NEEDED);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_check_case_number", null)
			.withVariables(Map.of("caseNumberAccepted", true)));
		assertThat(result.logMessage()).isNull();
		assertThat(result.rejected()).isFalse();
		verifyNoInteractions(failureHandlerMock, processReportServiceMock, externalTaskServiceMock);
	}

	@Test
	void anErrandInTimraWithoutCaseNumberIsRejectedAndSentBackToTheGate() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_case_number");
		when(errandServiceMock.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(CaseNumberCheck.MISSING);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_check_case_number", null)
			.withVariables(Map.of("caseNumberAccepted", false))
			.withRejection("Errand '4517d939-3bd9-40b1-80cb-a70585a071d9' in Timrå has no case number in timraCaseNumber"));
		assertThat(result.rejected()).isTrue();
		verifyNoInteractions(failureHandlerMock, processReportServiceMock, externalTaskServiceMock);
	}

	@Test
	void anErrandThatCannotBeReadFailsTheStep() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(errandServiceMock.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock)).isSameAs(problem);
	}

	@Test
	void anErrandThatCannotBeReadIsRetriedThroughTheFailureHandler() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Support Management is down");
		lenient().when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		lenient().when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		lenient().when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(errandServiceMock.checkCaseNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(problem);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(failureHandlerMock).handleException(org.mockito.ArgumentMatchers.eq(externalTaskServiceMock), org.mockito.ArgumentMatchers.eq(externalTaskMock),
			org.mockito.ArgumentMatchers.anyString());
		verifyNoMoreInteractions(failureHandlerMock);
	}
}
