package se.sundsvall.alkt.service;

import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandProcess;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toReportTarget;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	private final SupportManagementClient supportManagementClient;
	private final OperatonIntegration operatonIntegration;

	ProcessReportService(final SupportManagementClient supportManagementClient, final OperatonIntegration operatonIntegration) {
		this.supportManagementClient = supportManagementClient;
		this.operatonIntegration = operatonIntegration;
	}

	/**
	 * Reports where the instance stands still, or nothing at all when it waits for no message: it then ended or stands on
	 * a work step, and the work step reports for itself. A failure is logged and swallowed, since whatever brought the
	 * process here has already happened and undoing it is not an option.
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

	/** A report without an activity gets the task's, or Support Management overwrites the row's activity with null. */
	public void report(final ExternalTask externalTask, final ProcessStateReport report) {
		var placed = report;
		if (report.currentActivityId() == null) {
			placed = report.atActivity(externalTask.getActivityId());
		}
		report(toReportTarget(externalTask), placed);
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

		supportManagementClient.reportProcess(target.municipalityId(), target.namespace(), target.errandId(), target.processInstanceId(),
			toErrandProcess(target, report));
	}
}
