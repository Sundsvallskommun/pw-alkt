package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcessReport;
import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessSignal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.integration.operaton.ProcessModelCache.ModelElement;
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
import static org.mockito.Mockito.lenient;
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
	private static final ProcessActivity PHASE_ENTRY = new ProcessActivity().activityType("PHASE");
	private static final ProcessActivity TASK_ENTRY = new ProcessActivity().activityType("TASK");

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private OperatonIntegration operatonIntegrationMock;

	@Mock
	private ProcessLog processLogMock;

	@Mock
	private ExternalTask externalTaskMock;

	@InjectMocks
	private ProcessReportService service;

	@Test
	void reportsToTheRowOfTheTarget() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID);
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

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
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

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
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, ProcessStateReport.completed().atActivity("closure_phase"));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("closure_phase");
		verify(externalTaskMock, never()).getActivityId();
	}

	/**
	 * Support Management replaces the buttons on every report, so a live report from a work step carries the cancellation.
	 * An incident leaves the instance alive and listening, so it counts as live.
	 */
	@ParameterizedTest
	@MethodSource("liveReports")
	void offersTheProcessWideSignalsOnALiveReportFromAWorkStep(final ProcessStateReport report) {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.findProcessWideSignals(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(List.of(new AwaitingSignal("process_cancelled", "Process cancelled")));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, report);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getAwaitingSignals())
			.extracting(ProcessSignal::getName, ProcessSignal::getLabel)
			.containsExactly(tuple("process_cancelled", "Process cancelled"));
	}

	private static Stream<ProcessStateReport> liveReports() {
		return Stream.of(ProcessStateReport.running("external_task_create_asset", null), ProcessStateReport.retrying("RETRY", "Timeout"),
			ProcessStateReport.failed("INCIDENT", "Timeout"));
	}

	@Test
	void leavesTheSignalsOutOfACompletedReport() {
		mockExternalTask();
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, ProcessStateReport.completed());

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getAwaitingSignals()).isNullOrEmpty();
		verify(operatonIntegrationMock, never()).findProcessWideSignals(any(), any());
	}

	@Test
	void keepsTheSignalsAReportAlreadyCarries() {
		mockExternalTask();
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, ProcessStateReport.waiting("review_phase", "Review").withAwaitingSignals(List.of(new AwaitingSignal("review_completed", "Review completed"))));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getAwaitingSignals()).extracting(ProcessSignal::getName).containsExactly("review_completed");
		verifyNoInteractions(operatonIntegrationMock);
	}

	/** A report without its buttons beats no report at all. */
	@Test
	void reportsWithoutTheSignalsWhenTheyCannotBeRead() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.findProcessWideSignals(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Operaton is down"));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, ProcessStateReport.running("external_task_create_asset", null));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("RUNNING");
		assertThat(captor.getValue().getAwaitingSignals()).isNullOrEmpty();
	}

	private void mockExternalTask() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY);
		when(externalTaskMock.getId()).thenReturn(EXTERNAL_TASK_ID);
		lenient().when(externalTaskMock.getActivityId()).thenReturn("external_task_create_asset");
	}

	@Test
	void reportsTheWaitStateOfTheInstanceWithItsSignals() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		final var signal = new AwaitingSignal("review_completed", "Review completed");
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(Optional.of(new WaitState("review_phase", "Review", List.of(signal))));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

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

	@Test
	void namesAReportAfterTheModelWhenItCarriesNoName() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.labelOf(DEFINITION_ID, "external_task_create_asset")).thenReturn("Create asset");
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.report(externalTaskMock, ProcessStateReport.retrying("RETRY", "Timeout"));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("external_task_create_asset");
		assertThat(captor.getValue().getCurrentActivityName()).isEqualTo("Create asset");
	}

	@Test
	void reportStartedEntersThePhaseOfTheStepWhenTheRowStoodInAnother() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.phaseOf(DEFINITION_ID, "external_task_create_asset")).thenReturn(Optional.of(new ModelElement("decision_phase", "Decision")));
		when(operatonIntegrationMock.phaseOf(DEFINITION_ID, "investigation_phase")).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandProcess().processInstanceId("another-instance").currentActivityId("decision_phase"),
			new ErrandProcess().processInstanceId(PROCESS_INSTANCE_ID).currentActivityId("investigation_phase")));
		when(processLogMock.phaseEntered("decision_phase", "Decision")).thenReturn(PHASE_ENTRY);
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportStarted(externalTaskMock);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("RUNNING");
		assertThat(captor.getValue().getCurrentActivityId()).isEqualTo("external_task_create_asset");
		assertThat(captor.getValue().getActivities()).containsExactly(PHASE_ENTRY);
	}

	/** A phase that loops back to its own step is not entered again, so the row is read by the phase it stands in. */
	@Test
	void reportStartedEntersNoPhaseWhenTheRowStandsOnAStepOfTheSamePhase() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.phaseOf(DEFINITION_ID, "external_task_create_asset")).thenReturn(Optional.of(new ModelElement("decision_phase", "Decision")));
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandProcess().processInstanceId(PROCESS_INSTANCE_ID).currentActivityId("external_task_create_asset")));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportStarted(externalTaskMock);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).isEmpty();
		verifyNoInteractions(processLogMock);
	}

	@Test
	void reportStartedReadsNoRowForAStepOutsideAPhase() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);

		service.reportStarted(externalTaskMock);

		verify(supportManagementIntegrationMock, never()).getErrandProcesses(any(), any(), any());
		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), any());
	}

	/** A row that cannot be read costs the entry, not the report. */
	@Test
	void reportStartedReportsWithoutAnEntryWhenTheRowCannotBeRead() {
		mockExternalTask();
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.phaseOf(DEFINITION_ID, "external_task_create_asset")).thenReturn(Optional.of(new ModelElement("decision_phase", "Decision")));
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Support Management is down"));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportStarted(externalTaskMock);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).isEmpty();
	}

	@Test
	void reportDoneLeavesAnEntryForWhatTheStepDid() {
		mockExternalTask();
		when(processLogMock.taskDone(externalTaskMock, "Asset 'asset-id' found or created")).thenReturn(TASK_ENTRY);
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportDone(externalTaskMock, ProcessStateReport.completed().withLogMessage("Asset 'asset-id' found or created"));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getProcessStatus()).isEqualTo("COMPLETED");
		assertThat(captor.getValue().getActivities()).containsExactly(TASK_ENTRY);
	}

	@Test
	void reportDoneLeavesAWarningForAStepThatRefusedItsWork() {
		mockExternalTask();
		when(processLogMock.taskRejected(externalTaskMock, "No deficiencies")).thenReturn(TASK_ENTRY);
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportDone(externalTaskMock, ProcessStateReport.completed().withRejection("No deficiencies"));

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).containsExactly(TASK_ENTRY);
		verify(processLogMock).taskRejected(externalTaskMock, "No deficiencies");
		verifyNoMoreInteractions(processLogMock);
	}

	@Test
	void reportDoneLeavesNoEntryForAStepThatSaysNothing() {
		mockExternalTask();
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportDone(externalTaskMock, ProcessStateReport.completed());

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).isEmpty();
		verifyNoInteractions(processLogMock);
	}

	@Test
	void reportsTheWaitStateWithoutAnEntryWhenThePhaseIsUnchanged() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(Optional.of(new WaitState("review_phase", "Review", List.of())));
		when(operatonIntegrationMock.phaseOf(DEFINITION_ID, "external_task_notify_processing_started")).thenReturn(Optional.of(new ModelElement("review_phase", "Review")));
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new ErrandProcess().processInstanceId(PROCESS_INSTANCE_ID).currentActivityId("external_task_notify_processing_started")));
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportWaitState(target, DEFINITION_ID);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).isEmpty();
		verifyNoInteractions(processLogMock);
	}

	/** A process with no row yet has just started, and its first wait state opens its first phase. */
	@Test
	void reportsTheWaitStateWithAnEntryWhenTheProcessHasNoRowYet() {
		final var target = new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null);
		when(operatonIntegrationMock.findWaitState(PROCESS_INSTANCE_ID, DEFINITION_ID)).thenReturn(Optional.of(new WaitState("registration_phase", "Registration", List.of())));
		when(processLogMock.phaseEntered("registration_phase", "Registration")).thenReturn(PHASE_ENTRY);
		final var captor = ArgumentCaptor.forClass(ErrandProcessReport.class);

		service.reportWaitState(target, DEFINITION_ID);

		verify(supportManagementIntegrationMock).reportProcess(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(PROCESS_INSTANCE_ID), captor.capture());
		assertThat(captor.getValue().getActivities()).containsExactly(PHASE_ENTRY);
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
