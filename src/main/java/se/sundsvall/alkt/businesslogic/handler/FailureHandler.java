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
 * Reports a failed external task back to the engine, decrementing the remaining retries.
 * <p>
 * Note the parameter order of {@link ExternalTaskService#handleFailure}: the second argument is the
 * <b>error message</b>, not the worker id (no overload takes a worker id - the engine already knows which worker holds
 * the lock). The error message becomes the message of the incident that is raised once the retries are exhausted, so it
 * has to carry the failure reason for the incident list to be usable.
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

	/**
	 * Reports before handleFailure so that the message lands on the errand where the case worker sees it. A report that
	 * fails is logged and nothing more: handleFailure has to run regardless, or the task keeps its lock until it expires
	 * and Support Management is down anyway.
	 */
	private void reportFailure(final ExternalTask externalTask, final String message) {
		final var report = calculateRetries(externalTask) > 0
			? ProcessStateReport.retrying(ERROR_CODE_RETRY, message)
			: ProcessStateReport.failed(ERROR_CODE_INCIDENT, message);

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
