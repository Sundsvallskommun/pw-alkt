package se.sundsvall.alkt.businesslogic.handler;

import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.service.ProcessReportService;

import static java.util.Collections.emptyMap;

@Component
public class FailureHandler {

	// TODO: no error-code taxonomy exists yet; this is a placeholder until one does.
	private static final String ERROR_CODE_TASK_FAILED = "TASK_FAILED";

	private static final Logger LOG = LoggerFactory.getLogger(FailureHandler.class);

	private final int maxRetries;

	private final long retryTimeoutInMilliseconds;

	private final ProcessReportService processReportService;

	FailureHandler(
		final ProcessReportService processReportService,
		@Value("${camunda.worker.max.retries}") final int maxRetries,
		@Value("${camunda.worker.retry.timeout}") final long retryTimeoutInMilliseconds) {
		this.processReportService = processReportService;
		this.maxRetries = maxRetries;
		this.retryTimeoutInMilliseconds = retryTimeoutInMilliseconds;
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message) {
		reportFailure(externalTask, message);

		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			calculateRetries(externalTask),
			retryTimeoutInMilliseconds);
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message, Map<String, Object> variables) {
		reportFailure(externalTask, message);

		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			calculateRetries(externalTask),
			retryTimeoutInMilliseconds,
			variables,
			emptyMap());
	}

	private void reportFailure(final ExternalTask externalTask, final String message) {
		final var report = toFailureReport(externalTask, message);

		try {
			processReportService.reportProcessState(externalTask, report);
		} catch (final Exception e) {
			// Must not stop handleFailure from running below - that would leave the task locked with its retries
			// never decremented.
			LOG.error("Could not report {} for task {} to Support Management", report.status(), externalTask.getId(), e);
		}
	}

	private ProcessStateReport toFailureReport(final ExternalTask externalTask, final String message) {
		if (calculateRetries(externalTask) > 0) {
			return ProcessStateReport.retrying(ERROR_CODE_TASK_FAILED, message);
		}
		return ProcessStateReport.failed(ERROR_CODE_TASK_FAILED, message);
	}

	private int calculateRetries(ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> retries - 1)
			.orElse(maxRetries);
	}
}
