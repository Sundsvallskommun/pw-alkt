package se.sundsvall.alkt.service;

import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

	ProcessReportService(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	/**
	 * A report without an activity gets the task's, since Support Management overwrites the row's activity with null
	 * otherwise.
	 */
	public void report(final ExternalTask externalTask, final ProcessStateReport report) {
		report(toReportTarget(externalTask), report.currentActivityId() == null ? report.atActivity(externalTask.getActivityId()) : report);
	}

	/** A refused report throws ClientProblem. The caller decides what that means. */
	public void report(final ReportTarget target, final ProcessStateReport report) {
		LOG.info("Process instance {} of errand {} reports {} at activity {}{}",
			sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(target.errandId()), report.status(),
			sanitizeForLogging(report.currentActivityId()), report.error() == null ? "" : ": " + sanitizeForLogging(report.error().getMessage()));

		supportManagementClient.reportProcess(target.municipalityId(), target.namespace(), target.errandId(), target.processInstanceId(),
			toErrandProcess(target, report));
	}
}
