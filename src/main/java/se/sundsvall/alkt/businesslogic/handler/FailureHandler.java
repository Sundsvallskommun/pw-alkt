package se.sundsvall.alkt.businesslogic.handler;

import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyMap;

@Component
public class FailureHandler {

	private final int maxRetries;

	private final long retryTimeout;

	FailureHandler(
		@Value("${camunda.worker.max.retries}") final int maxRetries,
		@Value("${camunda.worker.retry.timeout}") final long retryTimeout) {
		this.maxRetries = maxRetries;
		this.retryTimeout = retryTimeout;
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message) {
		externalTaskService.handleFailure(externalTask.getId(), externalTask.getWorkerId(),
			message,
			calculateRetries(externalTask),
			retryTimeout);
	}

	public void handleException(ExternalTaskService externalTaskService, ExternalTask externalTask, String message, Map<String, Object> variables) {
		externalTaskService.handleFailure(externalTask.getId(), externalTask.getWorkerId(),
			message,
			calculateRetries(externalTask),
			retryTimeout,
			variables,
			emptyMap());
	}

	private int calculateRetries(ExternalTask externalTask) {
		return Optional.ofNullable(externalTask.getRetries())
			.map(retries -> retries - 1)
			.orElse(maxRetries);
	}
}
