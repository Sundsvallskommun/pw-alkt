package se.sundsvall.alkt.service;

import org.camunda.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.api.model.ProcessStatus;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Service
public class ProcessReportService {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessReportService.class);

	// TODO: call PUT /{municipalityId}/{namespace}/errands/{errandId}/processes/{processInstanceId} once Support Management
	// exposes it (DRAKEN-4736). Until then the state is only logged, so Support Management never learns that a
	// process finished and leaves its row on RUNNING for good.
	public void report(final ExternalTask externalTask, final ProcessStatus status, final String message) {
		LOG.info("Process instance {} of errand {} reports {} at activity {}: {}",
			sanitizeForLogging(externalTask.getProcessInstanceId()), sanitizeForLogging(externalTask.getBusinessKey()), status,
			sanitizeForLogging(externalTask.getActivityId()), sanitizeForLogging(message));
	}
}
