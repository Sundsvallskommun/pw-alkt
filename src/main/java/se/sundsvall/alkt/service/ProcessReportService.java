package se.sundsvall.alkt.service;

import java.util.List;
import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandProcess;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toReportTarget;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	private final SupportManagementIntegration supportManagementIntegration;
	private final OperatonIntegration operatonIntegration;

	ProcessReportService(final SupportManagementIntegration supportManagementIntegration, final OperatonIntegration operatonIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.operatonIntegration = operatonIntegration;
	}

	/**
	 * Reports nothing when the instance waits for no message: it then ended or stands on a work step, which reports for
	 * itself. A failure is swallowed, since whatever brought the process here has already happened.
	 */
	public void reportWaitState(final ReportTarget target, final String processDefinitionId) {
		try {
			operatonIntegration.findWaitState(target.processInstanceId(), processDefinitionId)
				.ifPresent(waitState -> report(target, ProcessStateReport.waiting(waitState.activityId(), waitState.activityName())
					.withAwaitingSignals(waitState.awaitingSignals())));
		} catch (final Exception e) {
			LOG.error("Could not report the wait state of process instance {} for errand {}", sanitizeForLogging(target.processInstanceId()),
				sanitizeForLogging(target.errandId()), e);
		}
	}

	/**
	 * A report without an activity gets the task's, or Support Management overwrites the row's activity with null. Any
	 * report but COMPLETED gets the buttons that listen in every phase, since Support Management replaces the list on every
	 * report and an incident leaves the instance alive.
	 */
	public void report(final ExternalTask externalTask, final ProcessStateReport report) {
		var placed = report;
		if (report.currentActivityId() == null) {
			placed = placed.atActivity(externalTask.getActivityId());
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
			toErrandProcess(target, report));
	}
}
