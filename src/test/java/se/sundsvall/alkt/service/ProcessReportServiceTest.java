package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandProcess;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@ExtendWith(MockitoExtension.class)
class ProcessReportServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = UUID.randomUUID().toString();
	private static final String PROCESS_INSTANCE_ID = UUID.randomUUID().toString();
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String EXTERNAL_TASK_ID = UUID.randomUUID().toString();

	@Mock
	private SupportManagementClient supportManagementClientMock;

	@Mock
	private ExternalTask externalTaskMock;

	@InjectMocks
	private ProcessReportService service;

	@Test
	void reportsToTheRowOfTheTarget() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID);
		final var captor = ArgumentCaptor.forClass(ErrandProcess.class);

		service.report(target, ProcessStateReport.failed("INCIDENT", "Timeout"));

		verify(supportManagementClientMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		verifyNoMoreInteractions(supportManagementClientMock);
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("FAILED");
		assertThat(captor.getValue().getProcessKey()).isEqualTo(PROCESS_KEY);
		assertThat(captor.getValue().getExternalTaskId()).isEqualTo(EXTERNAL_TASK_ID);
		assertThat(captor.getValue().getError()).isNotNull();
		assertThat(captor.getValue().getError().getCode()).isEqualTo("INCIDENT");
	}

	@Test
	void reportsFromAnExternalTaskUsingItsVariablesAndIds() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY);
		when(externalTaskMock.getId()).thenReturn(EXTERNAL_TASK_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_complete_process");
		final var captor = ArgumentCaptor.forClass(ErrandProcess.class);

		service.report(externalTaskMock, ProcessStateReport.completed().withErrandVersion(7L));

		verify(supportManagementClientMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		verifyNoMoreInteractions(supportManagementClientMock);
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("COMPLETED");
		assertThat(captor.getValue().getErrandVersion()).isEqualTo(7L);
		assertThat(captor.getValue().getProcessKey()).isEqualTo(PROCESS_KEY);
		assertThat(captor.getValue().getExternalTaskId()).isEqualTo(EXTERNAL_TASK_ID);
		// A report naming no activity is placed at the task's, so the row does not lose the one it has
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("external_task_complete_process");
	}

	@Test
	void keepsTheActivityAReportAlreadyNames() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY);
		when(externalTaskMock.getId()).thenReturn(EXTERNAL_TASK_ID);
		final var captor = ArgumentCaptor.forClass(ErrandProcess.class);

		service.report(externalTaskMock, ProcessStateReport.completed().atActivity("closure_phase"));

		verify(supportManagementClientMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("closure_phase");
		verify(externalTaskMock, never()).getActivityId();
	}

	/** A refused report is the caller's problem to handle, so nothing is swallowed here. */
	@Test
	void letsARefusedReportThrough() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID);
		when(supportManagementClientMock.reportProcess(any(), any(), any(), any(), any())).thenThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Precondition Failed"));

		assertThatThrownBy(() -> service.report(target, ProcessStateReport.completed()))
			.isInstanceOf(ClientProblem.class)
			.hasMessage("Bad Gateway: Precondition Failed");
	}
}
