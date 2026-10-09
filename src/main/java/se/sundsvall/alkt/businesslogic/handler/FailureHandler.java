package se.sundsvall.alkt.businesslogic.handler;

import java.util.List;
import java.util.Optional;
import org.camunda.bpm.client.exception.NotFoundException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.messaging.MessagingIntegration;
import se.sundsvall.alkt.service.ProcessLog;
import se.sundsvall.alkt.service.ProcessLog.Outcome;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_RETRY;
import static se.sundsvall.alkt.Constants.LOG_TASK_GONE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * Reports a failed external task back to the engine, to the activity log, and to Slack once it becomes an incident. The
 * description of the failure becomes the incident message once the retries are exhausted, so it carries the attempt and
 * the request id as well.
 */
@Component
public class FailureHandler {

	// Must match the errorCode of the bpmn:error that the boundary event of a skippable step catches.
	static final String BPMN_ERROR_STEP_SKIPPED = "step_skipped";

	private static final Logger LOG = LoggerFactory.getLogger(FailureHandler.class);

	private static final String INCIDENT_MESSAGE = "[%s][%s][%s] Incident in %s for errand %s (process instance %s): %s";
	private static final String SKIPPED_MESSAGE = "[%s][%s][%s] Skipped %s for errand %s (process instance %s): %s";

	private static final String RETRIED_FAILURE = "%s. Attempt %d of %d, x-request-id %s";
	private static final String UNRETRIED_FAILURE = "%s. Attempt %d, not retried, x-request-id %s";

	private final int maxRetries;

	private final long retryTimeoutInMilliseconds;

	private final ProcessReportService processReportService;

	private final ProcessLog processLog;

	private final MessagingIntegration messagingIntegration;

	FailureHandler(
		final ProcessReportService processReportService,
		final ProcessLog processLog,
		final MessagingIntegration messagingIntegration,
		@Value("${camunda.worker.max.retries}") final int maxRetries,
		@Value("${camunda.worker.retry.timeout}") final long retryTimeoutInMilliseconds) {
		this.processReportService = processReportService;
		this.processLog = processLog;
		this.messagingIntegration = messagingIntegration;
		this.maxRetries = maxRetries;
		this.retryTimeoutInMilliseconds = retryTimeoutInMilliseconds;
	}

	public void handleException(final ExternalTaskService externalTaskService, final ExternalTask externalTask, final String description) {
		handleFailure(externalTaskService, externalTask, describe(externalTask, description, true), calculateRetries(externalTask));
	}

	/** For a failure no retry can fix: the incident is raised and alerted on the first attempt. */
	public void handleIncident(final ExternalTaskService externalTaskService, final ExternalTask externalTask, final String description) {
		handleFailure(externalTaskService, externalTask, describe(externalTask, description, false), 0);
	}

	/**
	 * For a step the process can go on without: once the retries are spent the error is thrown into the model, where a
	 * boundary event takes the process past the step, instead of raising an incident. Returns true when it was thrown.
	 */
	public boolean handleSkippableFailure(final ExternalTaskService externalTaskService, final ExternalTask externalTask, final String description, final boolean retryable) {
		final var message = describe(externalTask, description, retryable);
		final var retries = retryable ? calculateRetries(externalTask) : 0;
		if (retries > 0) {
			handleFailure(externalTaskService, externalTask, message, retries);
			return false;
		}
		if (!tellEngine(externalTask, () -> externalTaskService.handleBpmnError(externalTask, BPMN_ERROR_STEP_SKIPPED, message))) {
			return false;
		}
		reportFailure(externalTask, ProcessStateReport.running(null, null), Outcome.SKIPPED, message);
		alert(SKIPPED_MESSAGE, externalTask, message);
		return true;
	}

	private void handleFailure(final ExternalTaskService externalTaskService, final ExternalTask externalTask, final String message, final int retries) {
		if (!tellEngine(externalTask, () -> externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			retries,
			retryTimeoutInMilliseconds))) {
			return;
		}

		if (retries > 0) {
			reportFailure(externalTask, ProcessStateReport.retrying(ERROR_CODE_RETRY, message), Outcome.RETRY, message);
			return;
		}
		reportFailure(externalTask, ProcessStateReport.failed(ERROR_CODE_INCIDENT, message), Outcome.FAILED, message);
		alert(INCIDENT_MESSAGE, externalTask, message);
	}

	/**
	 * A task that is gone was taken away by a cancellation or deletion; there is no failure to report or alert on. The
	 * engine is told first on purpose, so a cancelled step is not reported FAILED; any other fault in telling it skips the
	 * report and the alert.
	 */
	private static boolean tellEngine(final ExternalTask externalTask, final Runnable handleFailure) {
		try {
			handleFailure.run();
			return true;
		} catch (final NotFoundException e) {
			LOG.info(LOG_TASK_GONE, sanitizeForLogging(externalTask.getId()), sanitizeForLogging(externalTask.getProcessInstanceId()));
			return false;
		}
	}

	/** Best effort. A failed report is only logged, the alert must go out regardless. */
	private void reportFailure(final ExternalTask externalTask, final ProcessStateReport report, final Outcome outcome, final String message) {
		try {
			processReportService.report(externalTask, report.withActivities(List.of(processLog.taskFailed(externalTask, outcome, message))));
		} catch (final Exception e) {
			LOG.error("Could not report {} for task {} of process instance {} to Support Management", report.status(), sanitizeForLogging(externalTask.getId()),
				sanitizeForLogging(externalTask.getProcessInstanceId()), e);
		}
	}

	private void alert(final String template, final ExternalTask externalTask, final String message) {
		final String municipalityId = externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID);
		try {
			messagingIntegration.sendSlack(municipalityId, template.formatted(
				municipalityId,
				externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE),
				externalTask.getProcessDefinitionKey(),
				externalTask.getActivityId(),
				externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID),
				externalTask.getProcessInstanceId(),
				message));
		} catch (final Exception e) {
			LOG.error("Could not send the incident alert for task {} of process instance {}", sanitizeForLogging(externalTask.getId()),
				sanitizeForLogging(externalTask.getProcessInstanceId()), e);
		}
	}

	private String describe(final ExternalTask externalTask, final String description, final boolean retryable) {
		if (retryable) {
			return RETRIED_FAILURE.formatted(description, attemptOf(externalTask), maxRetries + 1, RequestId.get());
		}
		return UNRETRIED_FAILURE.formatted(description, attemptOf(externalTask), RequestId.get());
	}

	/**
	 * The engine holds no retries before the first failure and the retries left after it. Clamped, since a max.retries
	 * changed while a task is failing would otherwise count past the end.
	 */
	private int attemptOf(final ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> Math.clamp(maxRetries - retries + 2L, 1, maxRetries + 1))
			.orElse(1);
	}

	private int calculateRetries(final ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> retries - 1)
			.orElse(maxRetries);
	}
}
