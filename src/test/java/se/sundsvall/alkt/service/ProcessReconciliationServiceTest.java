package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.HistoricProcessInstanceDto;
import generated.se.sundsvall.operaton.HistoricProcessInstanceDto.StateEnum;
import generated.se.sundsvall.operaton.HistoricVariableInstanceDto;
import generated.se.sundsvall.operaton.IncidentDto;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;

@ExtendWith(MockitoExtension.class)
class ProcessReconciliationServiceTest {

	private static final String TENANT = "ALKT";
	private static final String ALL_PROCESS_KEYS = "alcohol-serving,alcohol-serving-addition,alcohol-serving-change,e-cigarette-sales,external-inspection,internal-inspection,low-alcohol-beer-sales,low-alcohol-beer-serving,tobacco-sales,tobacco-sales-change,tobacco-sales-closure";
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = UUID.randomUUID().toString();
	private static final String PROCESS_INSTANCE_ID = UUID.randomUUID().toString();
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String EXTERNAL_TASK_ID = UUID.randomUUID().toString();
	private static final OffsetDateTime INCIDENT_TIMESTAMP = OffsetDateTime.now().minusMinutes(10);
	private static final OffsetDateTime END_TIME = OffsetDateTime.now().minusMinutes(3);

	@Mock
	private OperatonClient operatonClientMock;

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private ProcessReportService processReportServiceMock;

	private ProcessReconciliationService service;

	@BeforeEach
	void setUp() {
		service = new ProcessReconciliationService(operatonClientMock, supportManagementIntegrationMock, processReportServiceMock, new ReconciliationProperties(Duration.ofHours(2)));
	}

	// Incidents

	@Test
	void reportsAnIncidentAsFailedOnTheErrand() {
		mockIncident(incident());
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, EXTERNAL_TASK_ID)), reportCaptor.capture());
		final var report = reportCaptor.getValue();
		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.currentActivityId()).isEqualTo("external_task_complete_process");
		assertThat(report.error().getCode()).isEqualTo("INCIDENT");
		assertThat(report.error().getMessage()).isEqualTo("Timeout against Support Management");
		assertThat(report.activities()).singleElement().satisfies(activity -> {
			assertThat(activity.getActivityType()).isEqualTo("INCIDENT");
			assertThat(activity.getActivityId()).isEqualTo("external_task_complete_process");
			assertThat(activity.getSeverity()).isEqualTo("ERROR");
			assertThat(activity.getErrorCode()).isEqualTo("INCIDENT");
			assertThat(activity.getMessage()).isEqualTo("Timeout against Support Management");
			assertThat(activity.getOccurredAt()).isEqualTo(INCIDENT_TIMESTAMP);
		});
		verifyNoMoreInteractions(processReportServiceMock, supportManagementIntegrationMock);
	}

	@Test
	void skipsAnIncidentAlreadyOnTheErrand() {
		mockIncident(incident());
		mockErrandProcesses(row("FAILED", "INCIDENT"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	/** The step was retried and moved on after the list was fetched. The incident is history, not news. */
	@Test
	void skipsAnIncidentWhenTheRowWasTouchedSince() {
		mockIncident(incident());
		mockErrandProcesses(row("RUNNING", null).modified(INCIDENT_TIMESTAMP.plusSeconds(1)));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void reportsWhenTheRowWasTouchedBeforeTheIncident() {
		mockIncident(incident());
		mockErrandProcesses(row("RETRYING", "RETRY").modified(INCIDENT_TIMESTAMP.minusSeconds(1)));

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), any());
	}

	/** A FAILED row for another reason, without an error, or for another instance does not count as reported. */
	@Test
	void reportsWhenNoRowSaysIncidentForThisInstance() {
		mockIncident(incident());
		final var otherInstance = row("FAILED", "INCIDENT").processInstanceId(UUID.randomUUID().toString());
		mockErrandProcesses(row("FAILED", "TERMINATED"), row("FAILED", null), otherInstance);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), any());
	}

	@Test
	void reportsWhenTheErrandHasNoRows() {
		mockIncident(incident());
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), any());
	}

	@Test
	void leavesTheExternalTaskIdOutForAFailedJob() {
		mockIncident(incident().incidentType("failedJob"));
		mockErrandProcesses(row("RUNNING", null));

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, null)), any());
	}

	@Test
	void skipsAnIncidentWhoseErrandIsGone() {
		mockIncident(incident());
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(HttpStatus.NOT_FOUND, "Not Found"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	/** Support Management refuses the instance for good, e.g. the errand already has a completed process. */
	@Test
	void acceptsARefusedReport() {
		mockIncident(incident());
		mockErrandProcesses(row("RUNNING", null));
		doThrow(new ClientProblem(HttpStatus.CONFLICT, "Process life over")).when(processReportServiceMock).report(any(ReportTarget.class), any());

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), any());
	}

	@Test
	void skipsAnInstanceWithoutAnErrandIdentity() {
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident()));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY)));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(List.of(
			variable(PROCESS_VARIABLE_REQUEST_ID, "abc"),
			variable(PROCESS_VARIABLE_ERRAND_ID, null)));

		service.reconcile();

		verifyNoInteractions(supportManagementIntegrationMock, processReportServiceMock);
	}

	@Test
	void skipsAnIncidentWhoseInstanceIsGoneFromTheEngine() {
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident()));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(Optional.empty());

		service.reconcile();

		verifyNoInteractions(supportManagementIntegrationMock, processReportServiceMock);
	}

	/** A fault on one incident must not stop the next one. */
	@Test
	void carriesOnWhenOneIncidentCannotBeReported() {
		final var secondInstanceId = UUID.randomUUID().toString();
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident(), incident().processInstanceId(secondInstanceId)));
		when(operatonClientMock.getHistoricProcessInstance(any())).thenReturn(Optional.of(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY)));
		when(operatonClientMock.getHistoricVariableInstances(any())).thenReturn(identity());
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Support Management is down")).when(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, EXTERNAL_TASK_ID)), any());

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, EXTERNAL_TASK_ID)), any());
		verify(processReportServiceMock).report(eq(target(secondInstanceId, EXTERNAL_TASK_ID)), any());
	}

	@Test
	void doesNothingWithoutIncidentsOrEndedInstances() {
		service.reconcile();

		verify(operatonClientMock).findIncidents(TENANT, ALL_PROCESS_KEYS);
		verify(operatonClientMock).findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any());
		verifyNoMoreInteractions(operatonClientMock);
		verifyNoInteractions(supportManagementIntegrationMock, processReportServiceMock);
	}

	/** Support Management requires occurredAt, but the engine leaves the incident timestamp nullable. */
	@Test
	void fallsBackToNowWhenTheIncidentHasNoTimestamp() {
		mockIncident(incident().incidentTimestamp(null));
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), reportCaptor.capture());
		assertThat(reportCaptor.getValue().activities()).singleElement()
			.satisfies(activity -> assertThat(activity.getOccurredAt()).isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES)));
	}

	// Ended instances

	@Test
	void looksBackAsFarAsConfiguredForEndedInstances() {
		final var captor = ArgumentCaptor.forClass(String.class);

		service.reconcile();

		verify(operatonClientMock).findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), captor.capture());
		final var finishedAfter = OffsetDateTime.parse(captor.getValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
		assertThat(finishedAfter).isCloseTo(OffsetDateTime.now().minusHours(2), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void settlesAnInstanceThatCompletedWithoutAFinalReport() {
		mockEndedInstance(StateEnum.COMPLETED);
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, null)), reportCaptor.capture());
		final var report = reportCaptor.getValue();
		assertThat(report.status()).isEqualTo(COMPLETED);
		assertThat(report.error()).isNull();
		assertThat(report.activities()).singleElement().satisfies(activity -> {
			assertThat(activity.getActivityType()).isEqualTo("RECONCILIATION");
			assertThat(activity.getSeverity()).isEqualTo("WARN");
			assertThat(activity.getErrorCode()).isNull();
			assertThat(activity.getMessage()).contains("COMPLETED");
			assertThat(activity.getOccurredAt()).isEqualTo(END_TIME);
		});
		verifyNoMoreInteractions(processReportServiceMock, supportManagementIntegrationMock);
	}

	@Test
	void settlesAnInternallyTerminatedInstanceAsCompleted() {
		mockEndedInstance(StateEnum.INTERNALLY_TERMINATED);
		mockErrandProcesses(row("WAITING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), reportCaptor.capture());
		assertThat(reportCaptor.getValue().status()).isEqualTo(COMPLETED);
	}

	@Test
	void settlesAnExternallyTerminatedInstanceAsFailed() {
		mockEndedInstance(StateEnum.EXTERNALLY_TERMINATED);
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), reportCaptor.capture());
		final var report = reportCaptor.getValue();
		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.error().getCode()).isEqualTo("TERMINATED");
		assertThat(report.activities()).singleElement().satisfies(activity -> {
			assertThat(activity.getSeverity()).isEqualTo("ERROR");
			assertThat(activity.getErrorCode()).isEqualTo("TERMINATED");
		});
	}

	@Test
	void leavesAnEndedInstanceWhoseRowIsAlreadyCompleted() {
		mockEndedInstance(StateEnum.COMPLETED);
		mockErrandProcesses(row("COMPLETED", null));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void leavesAnEndedInstanceWhoseRowIsAlreadyFailed() {
		mockEndedInstance(StateEnum.EXTERNALLY_TERMINATED);
		mockErrandProcesses(row("FAILED", "INCIDENT"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void leavesAnEndedInstanceWhoseErrandIsGone() {
		mockEndedInstance(StateEnum.EXTERNALLY_TERMINATED);
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(HttpStatus.NOT_FOUND, "Not Found"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	/** finished=true should keep these out, but a state that is not an end is never reported. */
	@Test
	void leavesAnInstanceThatHasNotEnded() {
		final var active = new HistoricProcessInstanceDto().id(PROCESS_INSTANCE_ID).processDefinitionKey(PROCESS_KEY).state(StateEnum.ACTIVE);
		final var unknown = new HistoricProcessInstanceDto().id(UUID.randomUUID().toString()).processDefinitionKey(PROCESS_KEY);
		when(operatonClientMock.findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any())).thenReturn(List.of(active, unknown));

		service.reconcile();

		verify(operatonClientMock, never()).getHistoricVariableInstances(any());
		verifyNoInteractions(supportManagementIntegrationMock, processReportServiceMock);
	}

	/** A fault on one instance must not stop the next one. */
	@Test
	void carriesOnWhenOneInstanceCannotBeSettled() {
		final var secondInstanceId = UUID.randomUUID().toString();
		when(operatonClientMock.findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any()))
			.thenReturn(List.of(endedInstance(PROCESS_INSTANCE_ID, StateEnum.COMPLETED), endedInstance(secondInstanceId, StateEnum.COMPLETED)));
		when(operatonClientMock.getHistoricVariableInstances(any())).thenReturn(identity());
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Support Management is down")).when(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, null)), any());

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID, null)), any());
		verify(processReportServiceMock).report(eq(target(secondInstanceId, null)), any());
	}

	/** Support Management requires occurredAt, but the engine leaves the end time nullable. */
	@Test
	void fallsBackToNowWhenTheEndedInstanceHasNoEndTime() {
		when(operatonClientMock.findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any()))
			.thenReturn(List.of(endedInstance(PROCESS_INSTANCE_ID, StateEnum.COMPLETED).endTime(null)));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(identity());
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), reportCaptor.capture());
		assertThat(reportCaptor.getValue().activities()).singleElement()
			.satisfies(activity -> assertThat(activity.getOccurredAt()).isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES)));
	}

	// Helpers

	private void mockIncident(final IncidentDto incident) {
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(Optional.of(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY)));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(identity());
	}

	private void mockEndedInstance(final StateEnum state) {
		when(operatonClientMock.findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any())).thenReturn(List.of(endedInstance(PROCESS_INSTANCE_ID, state)));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(identity());
	}

	private void mockErrandProcesses(final ErrandProcess... rows) {
		when(supportManagementIntegrationMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(rows));
	}

	private static HistoricProcessInstanceDto endedInstance(final String processInstanceId, final StateEnum state) {
		return new HistoricProcessInstanceDto().id(processInstanceId).processDefinitionKey(PROCESS_KEY).businessKey(ERRAND_ID).state(state).endTime(END_TIME);
	}

	private static IncidentDto incident() {
		return new IncidentDto()
			.id(UUID.randomUUID().toString())
			.processInstanceId(PROCESS_INSTANCE_ID)
			.incidentType("failedExternalTask")
			.activityId("external_task_complete_process")
			._configuration(EXTERNAL_TASK_ID)
			.incidentMessage("Timeout against Support Management")
			.incidentTimestamp(INCIDENT_TIMESTAMP);
	}

	private static List<HistoricVariableInstanceDto> identity() {
		return List.of(
			variable(PROCESS_VARIABLE_MUNICIPALITY_ID, MUNICIPALITY_ID),
			variable(PROCESS_VARIABLE_NAMESPACE, NAMESPACE),
			variable(PROCESS_VARIABLE_ERRAND_ID, ERRAND_ID),
			variable(PROCESS_VARIABLE_REQUEST_ID, "abc"));
	}

	private static HistoricVariableInstanceDto variable(final String name, final String value) {
		return new HistoricVariableInstanceDto().name(name).value(value);
	}

	private static ErrandProcess row(final String status, final String errorCode) {
		final var row = new ErrandProcess()
			.processInstanceId(PROCESS_INSTANCE_ID)
			.processKey(PROCESS_KEY)
			.processStatus(status);
		if (errorCode != null) {
			row.setError(new ProcessError().code(errorCode));
		}
		return row;
	}

	private static ReportTarget target(final String processInstanceId, final String externalTaskId) {
		return new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, processInstanceId, PROCESS_KEY, externalTaskId);
	}
}
