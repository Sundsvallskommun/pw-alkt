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

/**
 * The one way the state of a process reaches Support Management. Work steps report through it once they are done, the
 * failure handler when they are not, and the reconciliation when nobody else did.
 */
@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	private final SupportManagementClient supportManagementClient;

	ProcessReportService(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	public void report(final ExternalTask externalTask, final ProcessStateReport report) {
		report(toReportTarget(externalTask), report);
	}

	/**
	 * Sends the report. Anything Support Management answers with other than success is thrown as a
	 * {@code ClientProblem}: a 412 means the errand moved under the step and the step is to be run again, and the rest
	 * are faults the caller decides what to do about.
	 */
	public void report(final ReportTarget target, final ProcessStateReport report) {
		LOG.info("Process instance {} of errand {} reports {} at activity {}",
			sanitizeForLogging(target.processInstanceId()), sanitizeForLogging(target.errandId()), report.status(),
			sanitizeForLogging(report.currentActivityId()));

		supportManagementClient.reportProcess(target.municipalityId(), target.namespace(), target.errandId(), target.processInstanceId(),
			toErrandProcess(target, report));
	}
}
