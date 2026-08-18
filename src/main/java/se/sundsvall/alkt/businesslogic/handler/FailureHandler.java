package se.sundsvall.alkt.businesslogic.handler;

import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyMap;

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

	private final int maxRetries;

	private final long retryTimeoutInMilliseconds;

	FailureHandler(
		@Value("${camunda.worker.max.retries}") final int maxRetries,
		@Value("${camunda.worker.retry.timeout}") final long retryTimeoutInMilliseconds) {
		this.maxRetries = maxRetries;
		this.retryTimeoutInMilliseconds = retryTimeoutInMilliseconds;
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message) {
		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			calculateRetries(externalTask),
			retryTimeoutInMilliseconds);
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message, Map<String, Object> variables) {
		externalTaskService.handleFailure(externalTask.getId(),
			message, // errorMessage - surfaces as the incident message
			null, // errorDetails
			calculateRetries(externalTask),
			retryTimeoutInMilliseconds,
			variables,
			emptyMap());
	}

	private int calculateRetries(ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> retries - 1)
			.orElse(maxRetries);
	}
}
