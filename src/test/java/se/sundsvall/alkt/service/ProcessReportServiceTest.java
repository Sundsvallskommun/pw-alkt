package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ProcessSignal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.integration.operaton.WaitState;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
	private static final String DEFINITION_ID = "alcohol-serving:1:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private OperatonIntegration operatonIntegrationMock;

	@Mock
	private ExternalTask externalTaskMock;

	@InjectMocks
	private ProcessReportService service;

	@Test
	void reportsToTheRowOfTheTarget() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID);
		final var captor = ArgumentCaptor.forClass(ErrandProcess.class);

		service.report(target, ProcessStateReport.failed("INCIDENT", "Timeout"));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		verifyNoMoreInteractions(supportManagementIntegrationMock);
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

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		verifyNoMoreInteractions(supportManagementIntegrationMock);
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

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("closure_phase");
		verify(externalTaskMock, never()).getActivityId();
	}

	@Test
	void reportsTheWaitStateOfTheInstanceWithItsSignals() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		final var signal = new AwaitingSignal("review_completed", "Review completed");
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(Optional.of(new WaitState("review_phase", "Review", List.of(signal))));
		final var captor = ArgumentCaptor.forClass(ErrandProcess.class);

		service.reportWaitState(target, DEFINITION_ID);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("WAITING");
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("review_phase");
		assertThat(captor.getValue().getCurrentActivityName()).isEqualTo("Review");
		assertThat(captor.getValue().getAwaitingSignals())
			.extracting(ProcessSignal::getName, ProcessSignal::getLabel)
			.containsExactly(tuple("review_completed", "Review completed"));
	}

	/** No message subscription means the process ended or stands on a work step, and then the work step reports. */
	@Test
	void reportsNothingForAnInstanceThatWaitsForNothing() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(Optional.empty());

		service.reportWaitState(target, DEFINITION_ID);

		verifyNoInteractions(supportManagementIntegrationMock);
	}

	/** Whatever brought the process here has already happened, so a failed wait state report must not undo it. */
	@Test
	void swallowsAFailingWaitStateReport() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Operaton is down"));

		assertThatNoException().isThrownBy(() -> service.reportWaitState(target, DEFINITION_ID));
	}

	/** A refused report is the caller's problem to handle, so nothing is swallowed here. */
	@Test
	void letsARefusedReportThrough() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID);
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Precondition Failed")).when(supportManagementIntegrationMock).reportProcess(any(), any(), any(), any(), any());

		assertThatThrownBy(() -> service.report(target, ProcessStateReport.completed()))
			.isInstanceOf(ClientProblem.class)
			.hasMessage("Bad Gateway: Precondition Failed");
	}
}
