package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.HistoricProcessInstanceDto;
import generated.se.sundsvall.operaton.HistoricVariableInstanceDto;
import generated.se.sundsvall.operaton.IncidentDto;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.ProcessLog.Outcome;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;

import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.ACTIVITY_CANCEL_PROCESS;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_TERMINATED;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper.toOperatonTimestamp;
import static se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper.toProcessDefinitionKeyIn;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/** Tells Support Management what the work steps could not: an incident stands, or an instance is gone. */
@Service
public class ProcessReconciliationService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReconciliationService.class);

	private static final String INCIDENT_TYPE_FAILED_EXTERNAL_TASK = "failedExternalTask";
	private static final String MESSAGE_ENDED_WITHOUT_REPORT = "The process instance ended in the engine as %s without reporting it, settled by the reconciliation";

	private final OperatonClient operatonClient;
	private final SupportManagementIntegration supportManagementIntegration;
	private final ProcessReportService processReportService;
	private final ReconciliationProperties properties;
	private final ProcessLog processLog;

	ProcessReconciliationService(final OperatonClient operatonClient, final SupportManagementIntegration supportManagementIntegration, final ProcessReportService processReportService,
		final ReconciliationProperties properties, final ProcessLog processLog) {
		this.operatonClient = operatonClient;
		this.supportManagementIntegration = supportManagementIntegration;
		this.processReportService = processReportService;
		this.properties = properties;
		this.processLog = processLog;
	}

	public void reconcile() {
		reportIncidents();
		settleEndedInstances();
	}

	/** One incident failing must not stop the others, so each is handled on its own. */
	private void reportIncidents() {
		final var incidents = operatonClient.findIncidents(TENANT_ID_ALKT, toProcessDefinitionKeyIn(PROCESS_KEYS));
		if (!incidents.isEmpty()) {
			LOG.info("Found {} incidents in tenant {}", incidents.size(), TENANT_ID_ALKT);
		}

		for (final var incident : incidents) {
			try {
				reportIncident(incident);
			} catch (final Exception e) {
				LOG.error("Could not report incident {} of process instance {}", sanitizeForLogging(incident.getId()), sanitizeForLogging(incident.getProcessInstanceId()), e);
			}
		}
	}

	private void reportIncident(final IncidentDto incident) {
		final var processInstanceId = incident.getProcessInstanceId();
		final var instance = operatonClient.getHistoricProcessInstance(processInstanceId);
		if (instance.isEmpty()) {
			LOG.info("Process instance {} of incident {} is gone from the engine", sanitizeForLogging(processInstanceId), sanitizeForLogging(incident.getId()));
			return;
		}

		resolveTarget(processInstanceId, instance.get().getProcessDefinitionKey(), externalTaskIdOf(incident))
			.ifPresent(target -> reportUnless(target, row -> isReportedSince(row, incident), () -> toIncidentReport(incident, instance.get().getProcessDefinitionId())
				.withAwaitingSignals(processReportService.processWideSignalsOf(processInstanceId, instance.get().getProcessDefinitionId()))));
	}

	/** An instance that ended without a final report leaves the errand on RUNNING for good. */
	private void settleEndedInstances() {
		final var finishedAfter = toOperatonTimestamp(OffsetDateTime.now(UTC).minus(properties.lookback()));
		final var instances = operatonClient.findHistoricProcessInstances(TENANT_ID_ALKT, toProcessDefinitionKeyIn(PROCESS_KEYS), true, finishedAfter);
		if (!instances.isEmpty()) {
			LOG.info("Found {} instances in tenant {} that ended after {}", instances.size(), TENANT_ID_ALKT, finishedAfter);
		}

		for (final var instance : instances) {
			try {
				settleEndedInstance(instance);
			} catch (final Exception e) {
				LOG.error("Could not settle ended process instance {}", sanitizeForLogging(instance.getId()), e);
			}
		}
	}

	private void settleEndedInstance(final HistoricProcessInstanceDto instance) {
		final var ended = toEndedReport(instance);
		if (ended == null) {
			return;
		}

		resolveTarget(instance.getId(), instance.getProcessDefinitionKey(), null)
			.ifPresent(target -> reportUnless(target, row -> isTerminal(row) && !isIncident(row),
				() -> wasCancelled(instance) ? ended.atActivity(ACTIVITY_CANCEL_PROCESS) : ended));
	}

	/** The row tells a cancelled process from one that ran to its end by this activity, so the settled report keeps it. */
	private boolean wasCancelled(final HistoricProcessInstanceDto instance) {
		return instance.getState() == HistoricProcessInstanceDto.StateEnum.COMPLETED && operatonClient.getHistoricActivities(instance.getId()).stream()
			.anyMatch(activity -> ACTIVITY_CANCEL_PROCESS.equals(activity.getActivityId()));
	}

	/** The identity of an instance lives in its variables. Without it there is no row to report to. */
	private Optional<ReportTarget> resolveTarget(final String processInstanceId, final String processKey, final String externalTaskId) {
		final var variables = operatonClient.getHistoricVariableInstances(processInstanceId).stream()
			.filter(variable -> variable.getValue() != null)
			.collect(toMap(HistoricVariableInstanceDto::getName, variable -> String.valueOf(variable.getValue()), (first, second) -> first));

		final var municipalityId = variables.get(PROCESS_VARIABLE_MUNICIPALITY_ID);
		final var namespace = variables.get(PROCESS_VARIABLE_NAMESPACE);
		final var errandId = variables.get(PROCESS_VARIABLE_ERRAND_ID);
		if (isAnyBlank(municipalityId, namespace, errandId)) {
			LOG.warn("Process instance {} carries no errand identity, so there is nothing to report to", sanitizeForLogging(processInstanceId));
			return Optional.empty();
		}

		return Optional.of(new ReportTarget(municipalityId, namespace, errandId, processInstanceId, processKey, externalTaskId));
	}

	/**
	 * Support Management decides what is already reported. 404 (errand gone) and 409 (refused for good) are not retried.
	 */
	private void reportUnless(final ReportTarget target, final Predicate<ErrandProcess> alreadyThere, final Supplier<ProcessStateReport> toReport) {
		try {
			final var done = rowsOf(target)
				.filter(row -> target.processInstanceId().equals(row.getProcessInstanceId()))
				.anyMatch(alreadyThere);
			if (done) {
				return;
			}

			final var report = toReport.get();
			LOG.warn("Process instance {} of errand {} is reported {} by the reconciliation", sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(target.errandId()), report.status());
			processReportService.report(target, report);
		} catch (final ClientProblem e) {
			if (NOT_FOUND.equals(e.getStatus())) {
				LOG.info("Errand {} of process instance {} is gone from Support Management", sanitizeForLogging(target.errandId()), sanitizeForLogging(target.processInstanceId()));
				return;
			}
			if (CONFLICT.equals(e.getStatus())) {
				LOG.info("Support Management refuses process instance {} of errand {}: {}", sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(target.errandId()),
					sanitizeForLogging(e.getMessage()));
				return;
			}
			throw e;
		}
	}

	private Stream<ErrandProcess> rowsOf(final ReportTarget target) {
		return supportManagementIntegration.getErrandProcesses(target.municipalityId(), target.namespace(), target.errandId()).stream();
	}

	/** A row touched after the incident arose means the step was retried and moved on; the incident is history. */
	private static boolean isReportedSince(final ErrandProcess row, final IncidentDto incident) {
		if (isIncident(row)) {
			return true;
		}
		return row.getModified() != null && incident.getIncidentTimestamp() != null && row.getModified().isAfter(incident.getIncidentTimestamp());
	}

	private static boolean isIncident(final ErrandProcess row) {
		return FAILED.name().equals(row.getProcessStatus()) && row.getError() != null && ERROR_CODE_INCIDENT.equals(row.getError().getCode());
	}

	// An incident row is FAILED but not settled: the instance lived on and may since have been cancelled or terminated.
	private static boolean isTerminal(final ErrandProcess row) {
		return COMPLETED.name().equals(row.getProcessStatus()) || FAILED.name().equals(row.getProcessStatus());
	}

	/** The payload is the external task id for a failed external task, a job id otherwise. */
	private static String externalTaskIdOf(final IncidentDto incident) {
		if (INCIDENT_TYPE_FAILED_EXTERNAL_TASK.equals(incident.getIncidentType())) {
			return incident.getConfiguration();
		}
		return null;
	}

	/** Ends the model chose become COMPLETED, a cancellation from outside (e.g. Cockpit) becomes FAILED. */
	private ProcessStateReport toEndedReport(final HistoricProcessInstanceDto instance) {
		if (instance.getState() == null) {
			return null;
		}
		final var message = MESSAGE_ENDED_WITHOUT_REPORT.formatted(instance.getState().getValue());
		return switch (instance.getState()) {
			case COMPLETED, INTERNALLY_TERMINATED -> ProcessStateReport.completed()
				.withActivities(List.of(processLog.settled(Outcome.SETTLED_COMPLETED, message, instance.getEndTime())));
			case EXTERNALLY_TERMINATED -> ProcessStateReport.failed(ERROR_CODE_TERMINATED, message)
				.withActivities(List.of(processLog.settled(Outcome.SETTLED_TERMINATED, message, instance.getEndTime())));
			default -> null;
		};
	}

	private ProcessStateReport toIncidentReport(final IncidentDto incident, final String processDefinitionId) {
		final var failed = ProcessStateReport.failed(ERROR_CODE_INCIDENT, incident.getIncidentMessage());
		final var activity = processLog.incident(processDefinitionId, incident.getActivityId(), failed.error().getMessage(), incident.getIncidentTimestamp());

		return failed.atActivity(incident.getActivityId()).withActivities(List.of(activity));
	}
}
