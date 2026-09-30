package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.util.List;
import java.util.stream.Stream;
import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.integration.operaton.ProcessModelCache.ModelElement;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandProcessReport;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toReportTarget;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	private final SupportManagementIntegration supportManagementIntegration;
	private final OperatonIntegration operatonIntegration;
	private final ProcessLog processLog;

	ProcessReportService(final SupportManagementIntegration supportManagementIntegration, final OperatonIntegration operatonIntegration, final ProcessLog processLog) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.operatonIntegration = operatonIntegration;
		this.processLog = processLog;
	}

	/**
	 * Reports nothing when the instance waits for no message: it then ended or stands on a work step, which reports for
	 * itself. A failure is swallowed, since whatever brought the process here has already happened.
	 */
	public void reportWaitState(final ReportTarget target, final String processDefinitionId) {
		try {
			operatonIntegration.findWaitState(target.processInstanceId(), processDefinitionId)
				.ifPresent(waitState -> report(target, ProcessStateReport.waiting(waitState.activityId(), waitState.activityName())
					.withAwaitingSignals(waitState.awaitingSignals())
					.withActivities(phaseEntryOf(target, processDefinitionId, new ModelElement(waitState.activityId(), waitState.activityName())))));
		} catch (final Exception e) {
			LOG.error("Could not report the wait state of process instance {} for errand {}", sanitizeForLogging(target.processInstanceId()),
				sanitizeForLogging(target.errandId()), e);
		}
	}

	/** A phase that opens with a work step is entered here, since the process is past its gate before it waits again. */
	public void reportStarted(final ExternalTask externalTask) {
		final var phaseEntry = operatonIntegration.phaseOf(externalTask.getProcessDefinitionId(), externalTask.getActivityId())
			.map(phase -> phaseEntryOf(toReportTarget(externalTask), externalTask.getProcessDefinitionId(), phase))
			.orElse(List.of());

		report(externalTask, ProcessStateReport.running(externalTask.getActivityId(), null).withActivities(phaseEntry));
	}

	/** A step that says what it did leaves an entry in the activity log; one that says nothing did nothing worth one. */
	public void reportDone(final ExternalTask externalTask, final ProcessStateReport report) {
		if (report.logMessage() == null) {
			report(externalTask, report);
			return;
		}
		report(externalTask, report.withActivities(Stream.concat(report.activities().stream(), Stream.of(processLog.taskDone(externalTask, report.logMessage()))).toList()));
	}

	/**
	 * A report without an activity gets the task's, or Support Management overwrites the row's activity with null, and
	 * the name the model gives it. Any report but COMPLETED gets the buttons that listen in every phase, since Support
	 * Management replaces the list on every report and an incident leaves the instance alive.
	 */
	public void report(final ExternalTask externalTask, final ProcessStateReport report) {
		var placed = report;
		if (report.currentActivityId() == null) {
			placed = placed.atActivity(externalTask.getActivityId());
		}
		if (placed.currentActivityName() == null) {
			placed = placed.named(operatonIntegration.labelOf(externalTask.getProcessDefinitionId(), placed.currentActivityId()));
		}
		if (report.status() != COMPLETED && report.awaitingSignals().isEmpty()) {
			placed = placed.withAwaitingSignals(processWideSignalsOf(externalTask.getProcessInstanceId(), externalTask.getProcessDefinitionId()));
		}
		report(toReportTarget(externalTask), placed);
	}

	// A report without its buttons beats no report at all.
	public List<AwaitingSignal> processWideSignalsOf(final String processInstanceId, final String processDefinitionId) {
		try {
			return operatonIntegration.findProcessWideSignals(processInstanceId, processDefinitionId);
		} catch (final Exception e) {
			LOG.warn("Could not read the process-wide signals of process instance {}", sanitizeForLogging(processInstanceId), e);
			return List.of();
		}
	}

	/** A refused report throws ClientProblem. The caller decides what that means. */
	public void report(final ReportTarget target, final ProcessStateReport report) {
		var error = "";
		if (report.error() != null) {
			error = ": " + sanitizeForLogging(report.error().getMessage());
		}
		LOG.info("Process instance {} of errand {} reports {} at activity {}{}",
			sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(target.errandId()), report.status(),
			sanitizeForLogging(report.currentActivityId()), error);

		supportManagementIntegration.reportProcess(target.municipalityId(), target.namespace(), target.errandId(), target.processInstanceId(),
			toErrandProcessReport(target, report));
	}

	/**
	 * The row says where the process stood before. Both sides are reduced to their phase, since the row may stand on a step
	 * inside it, and a phase that loops back to its own step must not be entered again. A row that cannot be read gives no
	 * entry rather than no report.
	 */
	private List<ProcessActivity> phaseEntryOf(final ReportTarget target, final String processDefinitionId, final ModelElement phase) {
		try {
			final var previousPhase = supportManagementIntegration.getErrandProcesses(target.municipalityId(), target.namespace(), target.errandId()).stream()
				.filter(row -> target.processInstanceId().equals(row.getProcessInstanceId()))
				.findFirst()
				.map(ErrandProcess::getCurrentActivityId)
				.map(activityId -> operatonIntegration.phaseOf(processDefinitionId, activityId).map(ModelElement::id).orElse(activityId));
			if (previousPhase.filter(phase.id()::equals).isPresent()) {
				return List.of();
			}
			return List.of(processLog.phaseEntered(phase.id(), phase.name()));
		} catch (final Exception e) {
			LOG.warn("Could not tell whether process instance {} entered phase {}", sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(phase.id()), e);
			return List.of();
		}
	}
}
