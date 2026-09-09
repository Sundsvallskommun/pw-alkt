package se.sundsvall.alkt.service;

import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	private final SupportManagementIntegration supportManagementIntegration;

	ProcessReportService(final SupportManagementIntegration supportManagementIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
	}

	public void reportProcessState(final ExternalTask externalTask, final ProcessStateReport report) {
		LOG.info("Process instance {} of errand {} reports {} at activity {}",
			sanitizeForLogging(externalTask.getProcessInstanceId()), sanitizeForLogging(externalTask.getBusinessKey()), report.status(),
			sanitizeForLogging(externalTask.getActivityId()));

		final String municipalityId = externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID);
		final String namespace = externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE);
		final String errandId = externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID);

		supportManagementIntegration.patchProcessState(municipalityId, namespace, errandId, report);
	}
}
