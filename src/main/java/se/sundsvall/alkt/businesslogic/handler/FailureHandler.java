package se.sundsvall.alkt.businesslogic.handler;

import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static java.util.Collections.emptyMap;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_RETRY;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * Reports a failed external task back to the engine. The second argument of handleFailure is the error message, not
 * the worker id; it becomes the incident message once the retries are exhausted.
 */
@Component
public class FailureHandler {

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
		final var retries = calculateRetries(externalTask);
		reportFailure(externalTask, message, retries);

		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			retries,
			retryTimeoutInMilliseconds);
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message, Map<String, Object> variables) {
		final var retries = calculateRetries(externalTask);
		reportFailure(externalTask, message, retries);

		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			retries,
			retryTimeoutInMilliseconds,
			variables,
			emptyMap());
	}

	/** Best effort. A failed report is only logged, handleFailure must run regardless or the task keeps its lock. */
	private void reportFailure(final ExternalTask externalTask, final String message, final int retries) {
		var report = ProcessStateReport.failed(ERROR_CODE_INCIDENT, message);
		if (retries > 0) {
			report = ProcessStateReport.retrying(ERROR_CODE_RETRY, message);
		}

		try {
			processReportService.report(externalTask, report);
		} catch (final Exception e) {
			LOG.error("Could not report {} for task {} of process instance {} to Support Management", report.status(), sanitizeForLogging(externalTask.getId()),
				sanitizeForLogging(externalTask.getProcessInstanceId()), e);
		}
	}

	private int calculateRetries(ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> retries - 1)
			.orElse(maxRetries);
	}
}
