package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.HistoricProcessInstanceDto;
import generated.se.sundsvall.operaton.HistoricProcessInstanceDto.StateEnum;
import generated.se.sundsvall.operaton.HistoricVariableInstanceDto;
import generated.se.sundsvall.operaton.IncidentDto;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
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
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;

@ExtendWith(MockitoExtension.class)
class ProcessReconciliationServiceTest {

	private static final String TENANT = "ALKT";
	private static final String ALL_PROCESS_KEYS = "alcohol-serving,alcohol-serving-addition,alcohol-serving-change,e-cigarette-sales,low-alcohol-beer-sales,low-alcohol-beer-serving,supervision,tobacco-sales,tobacco-sales-change,tobacco-sales-closure";
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = UUID.randomUUID().toString();
	private static final String PROCESS_INSTANCE_ID = UUID.randomUUID().toString();
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String EXTERNAL_TASK_ID = UUID.randomUUID().toString();
	private static final OffsetDateTime INCIDENT_TIMESTAMP = OffsetDateTime.now();
	private static final OffsetDateTime END_TIME = OffsetDateTime.now().minusMinutes(3);

	@Mock
	private OperatonClient operatonClientMock;

	@Mock
	private SupportManagementClient supportManagementClientMock;

	@Mock
	private ProcessReportService processReportServiceMock;

	private ProcessReconciliationService service;

	@BeforeEach
	void setUp() {
		service = new ProcessReconciliationService(operatonClientMock, supportManagementClientMock, processReportServiceMock, new ReconciliationProperties(Duration.ofHours(24)));
	}

	@Test
	void looksBackAsFarAsConfiguredForEndedInstances() {
		final var captor = ArgumentCaptor.forClass(String.class);

		service.reconcile();

		verify(operatonClientMock).findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), captor.capture());
		final var finishedAfter = OffsetDateTime.parse(captor.getValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
		assertThat(finishedAfter).isCloseTo(OffsetDateTime.now().minusHours(24), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void settlesAnInstanceThatCompletedWithoutAFinalReport() {
		mockEndedInstance(StateEnum.COMPLETED);
		mockErrandProcesses(row("RUNNING", null));
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(eq(new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, null)), reportCaptor.capture());
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
	void leavesAnEndedInstanceWhoseRowIsAlreadyTerminal() {
		mockEndedInstance(StateEnum.COMPLETED);
		mockErrandProcesses(row("COMPLETED", null));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void leavesAnEndedInstanceWhoseErrandIsGone() {
		mockEndedInstance(StateEnum.EXTERNALLY_TERMINATED);
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(HttpStatus.NOT_FOUND, "Not Found"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void reportsAnIncidentAsFailedOnTheErrand() {
		mockInstanceWithIdentity();
		mockErrandProcesses(row("RUNNING", null));
		final var targetCaptor = ArgumentCaptor.forClass(ReportTarget.class);
		final var reportCaptor = ArgumentCaptor.forClass(ProcessStateReport.class);

		service.reconcile();

		verify(processReportServiceMock).report(targetCaptor.capture(), reportCaptor.capture());
		assertThat(targetCaptor.getValue()).isEqualTo(new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, PROCESS_KEY, EXTERNAL_TASK_ID));
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
		verify(operatonClientMock).findIncidents(TENANT, ALL_PROCESS_KEYS);
	}

	@Test
	void skipsAnIncidentAlreadyOnTheErrand() {
		mockInstanceWithIdentity();
		mockErrandProcesses(row("FAILED", "INCIDENT"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	/** A FAILED row for another reason, or an older instance of the errand, does not count as reported. */
	@Test
	void reportsWhenTheFailedRowIsNotAnIncidentOrNotThisInstance() {
		mockInstanceWithIdentity();
		final var otherInstance = row("FAILED", "INCIDENT").processInstanceId(UUID.randomUUID().toString());
		mockErrandProcesses(row("FAILED", "TERMINATED"), otherInstance);

		service.reconcile();

		verify(processReportServiceMock).report(any(ReportTarget.class), any());
	}

	@Test
	void skipsAnIncidentWhoseErrandIsGone() {
		mockInstanceWithIdentity();
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenThrow(new ClientProblem(HttpStatus.NOT_FOUND, "Not Found"));

		service.reconcile();

		verifyNoInteractions(processReportServiceMock);
	}

	@Test
	void skipsAnInstanceWithoutAnErrandIdentity() {
		final var incident = incident();
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(List.of(variable(PROCESS_VARIABLE_REQUEST_ID, "abc")));

		service.reconcile();

		verifyNoInteractions(supportManagementClientMock, processReportServiceMock);
	}

	@Test
	void skipsAnIncidentWhoseInstanceIsGoneFromTheEngine() {
		final var incident = incident();
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(null);

		service.reconcile();

		verify(operatonClientMock, never()).getHistoricVariableInstances(any());
		verifyNoInteractions(supportManagementClientMock, processReportServiceMock);
	}

	/** A refused report on one incident must not stop the next one. */
	@Test
	void carriesOnWhenOneReportIsRefused() {
		final var first = incident();
		final var secondInstanceId = UUID.randomUUID().toString();
		final var second = incident().processInstanceId(secondInstanceId);
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(first, second));
		when(operatonClientMock.getHistoricProcessInstance(any())).thenReturn(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY));
		when(operatonClientMock.getHistoricVariableInstances(any())).thenReturn(identity());
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(new ErrandProcesses()));
		doThrow(new ClientProblem(HttpStatus.CONFLICT, "Another live instance")).when(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID)), any());

		service.reconcile();

		verify(processReportServiceMock).report(eq(target(PROCESS_INSTANCE_ID)), any());
		verify(processReportServiceMock).report(eq(target(secondInstanceId)), any());
	}

	@Test
	void doesNothingWithoutIncidents() {
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of());

		service.reconcile();

		verifyNoInteractions(supportManagementClientMock, processReportServiceMock);
	}

	private void mockEndedInstance(final StateEnum state) {
		final var instance = new HistoricProcessInstanceDto().id(PROCESS_INSTANCE_ID).processDefinitionKey(PROCESS_KEY).businessKey(ERRAND_ID).state(state).endTime(END_TIME);
		when(operatonClientMock.findHistoricProcessInstances(eq(TENANT), eq(ALL_PROCESS_KEYS), eq(true), any())).thenReturn(List.of(instance));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(identity());
	}

	private void mockInstanceWithIdentity() {
		when(operatonClientMock.findIncidents(TENANT, ALL_PROCESS_KEYS)).thenReturn(List.of(incident()));
		when(operatonClientMock.getHistoricProcessInstance(PROCESS_INSTANCE_ID)).thenReturn(new HistoricProcessInstanceDto().processDefinitionKey(PROCESS_KEY));
		when(operatonClientMock.getHistoricVariableInstances(PROCESS_INSTANCE_ID)).thenReturn(identity());
	}

	private void mockErrandProcesses(final ErrandProcess... rows) {
		when(supportManagementClientMock.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(ResponseEntity.ok(new ErrandProcesses().processes(List.of(rows))));
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

	private static ReportTarget target(final String processInstanceId) {
		return new ReportTarget(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, processInstanceId, PROCESS_KEY, EXTERNAL_TASK_ID);
	}
}
