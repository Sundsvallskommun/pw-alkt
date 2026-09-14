package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.HistoricVariableInstanceDto;
import generated.se.sundsvall.operaton.IncidentDto;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper.toProcessDefinitionKeyIn;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/** Tells Support Management what the work steps could not: an incident stands, or an instance is gone. */
@Service
public class ProcessReconciliationService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReconciliationService.class);

	private static final String ACTIVITY_TYPE_INCIDENT = "INCIDENT";
	private static final String SEVERITY_ERROR = "ERROR";

	private final OperatonClient operatonClient;
	private final SupportManagementClient supportManagementClient;
	private final ProcessReportService processReportService;

	ProcessReconciliationService(final OperatonClient operatonClient, final SupportManagementClient supportManagementClient, final ProcessReportService processReportService) {
		this.operatonClient = operatonClient;
		this.supportManagementClient = supportManagementClient;
		this.processReportService = processReportService;
	}

	public void reconcile() {
		reportIncidents();
	}

	/** One incident failing must not stop the others, so each is handled on its own. */
	private void reportIncidents() {
		final var incidents = operatonClient.findIncidents(TENANT_ID_ALKT, toProcessDefinitionKeyIn(PROCESS_KEYS));
		LOG.info("Found {} incidents in tenant {}", incidents.size(), TENANT_ID_ALKT);

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
		if (instance == null) {
			LOG.info("Process instance {} of incident {} is gone from the engine", sanitizeForLogging(processInstanceId), sanitizeForLogging(incident.getId()));
			return;
		}

		resolveTarget(processInstanceId, instance.getProcessDefinitionKey(), incident.getConfiguration()).ifPresent(target -> {
			try {
				if (isReportedAsIncident(target)) {
					LOG.debug("Incident {} on process instance {} is already on the errand", sanitizeForLogging(incident.getId()), sanitizeForLogging(processInstanceId));
					return;
				}
				processReportService.report(target, toIncidentReport(incident));
			} catch (final ClientProblem e) {
				if (NOT_FOUND.equals(e.getStatus())) {
					LOG.info("Errand {} of process instance {} is gone from Support Management", sanitizeForLogging(target.errandId()), sanitizeForLogging(processInstanceId));
					return;
				}
				throw e;
			}
		});
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

	/** Support Management decides what is already reported, so pw keeps no memory that a restart would lose. */
	private boolean isReportedAsIncident(final ReportTarget target) {
		return ofNullable(supportManagementClient.getErrandProcesses(target.municipalityId(), target.namespace(), target.errandId()).getBody())
			.map(ErrandProcesses::getProcesses)
			.orElse(List.of()).stream()
			.filter(process -> target.processInstanceId().equals(process.getProcessInstanceId()))
			.anyMatch(ProcessReconciliationService::isIncident);
	}

	private static boolean isIncident(final ErrandProcess process) {
		return FAILED.name().equals(process.getProcessStatus()) && process.getError() != null && ERROR_CODE_INCIDENT.equals(process.getError().getCode());
	}

	private static ProcessStateReport toIncidentReport(final IncidentDto incident) {
		final var failed = ProcessStateReport.failed(ERROR_CODE_INCIDENT, incident.getIncidentMessage());
		final var activity = new ProcessActivity()
			.activityType(ACTIVITY_TYPE_INCIDENT)
			.activityId(incident.getActivityId())
			.severity(SEVERITY_ERROR)
			.errorCode(ERROR_CODE_INCIDENT)
			.message(failed.error().getMessage())
			.occurredAt(incident.getIncidentTimestamp());

		return new ProcessStateReport(FAILED, incident.getActivityId(), null, failed.error(), List.of(activity));
	}
}
