package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.configuration.ProcessLogProperties;
import se.sundsvall.alkt.configuration.ProcessLogProperties.PhaseTexts;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.dept44.requestid.RequestId;

import static java.time.ZoneOffset.UTC;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static se.sundsvall.alkt.Constants.ACTIVITY_TYPE_INCIDENT;
import static se.sundsvall.alkt.Constants.ACTIVITY_TYPE_PHASE;
import static se.sundsvall.alkt.Constants.ACTIVITY_TYPE_RECONCILIATION;
import static se.sundsvall.alkt.Constants.ACTIVITY_TYPE_TASK;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_RETRY;
import static se.sundsvall.alkt.Constants.ERROR_CODE_SKIPPED;
import static se.sundsvall.alkt.Constants.ERROR_CODE_TERMINATED;
import static se.sundsvall.alkt.Constants.SEVERITY_ERROR;
import static se.sundsvall.alkt.Constants.SEVERITY_INFO;
import static se.sundsvall.alkt.Constants.SEVERITY_WARN;

/**
 * Writes the entries of the activity log: activityName is what the case worker reads, message is for whoever debugs.
 * The texts come from configuration, then from the name in the model, then from the id.
 */
@Component
public class ProcessLog {

	public enum Outcome {
		DONE,
		RETRY,
		FAILED,
		SKIPPED,
		SETTLED_COMPLETED,
		SETTLED_TERMINATED
	}

	// The lengths Support Management accepts. A longer value costs the whole report, so it is cut here.
	private static final int MAX_ID_LENGTH = 255;
	private static final int MAX_NAME_LENGTH = 255;
	private static final int MAX_ERROR_CODE_LENGTH = 64;
	private static final int MAX_MESSAGE_LENGTH = 2048;

	private final ProcessLogProperties properties;
	private final OperatonIntegration operatonIntegration;

	ProcessLog(final ProcessLogProperties properties, final OperatonIntegration operatonIntegration) {
		this.properties = properties;
		this.operatonIntegration = operatonIntegration;
	}

	public ProcessActivity taskDone(final ExternalTask externalTask, final String logMessage) {
		return entry(ACTIVITY_TYPE_TASK, externalTask.getActivityId(), nameOf(externalTask, Outcome.DONE), SEVERITY_INFO, null,
			"%s, x-request-id %s".formatted(logMessage, RequestId.get()), now());
	}

	/** The attempt goes into the id, or Support Management would take every retry of the task for the first one. */
	public ProcessActivity taskFailed(final ExternalTask externalTask, final Outcome outcome, final int attempt, final String technicalMessage) {
		final var severity = outcome == Outcome.FAILED ? SEVERITY_ERROR : SEVERITY_WARN;
		final var errorCode = switch (outcome) {
			case FAILED -> ERROR_CODE_INCIDENT;
			case SKIPPED -> ERROR_CODE_SKIPPED;
			default -> ERROR_CODE_RETRY;
		};
		return entry(ACTIVITY_TYPE_TASK, "%s#%d".formatted(externalTask.getActivityId(), attempt), nameOf(externalTask, outcome), severity, errorCode,
			technicalMessage, now());
	}

	public ProcessActivity phaseEntered(final String phaseId, final String phaseName) {
		final var name = Optional.ofNullable(properties.phases().get(phaseId))
			.map(PhaseTexts::entered)
			.orElseGet(() -> orId(phaseName, phaseId));
		return entry(ACTIVITY_TYPE_PHASE, phaseId, name, SEVERITY_INFO, null, "Phase '%s' entered".formatted(phaseId), now());
	}

	/** The id is the activity alone, as it always was, so that an incident already in the log is not written again. */
	public ProcessActivity incident(final String processDefinitionId, final String activityId, final String message, final OffsetDateTime occurredAt) {
		final var name = stepText(activityId, Outcome.FAILED).orElseGet(() -> orId(operatonIntegration.labelOf(processDefinitionId, activityId), activityId));
		return entry(ACTIVITY_TYPE_INCIDENT, activityId, name, SEVERITY_ERROR, ERROR_CODE_INCIDENT, message, orNow(occurredAt));
	}

	public ProcessActivity settled(final Outcome outcome, final String message, final OffsetDateTime occurredAt) {
		if (outcome == Outcome.SETTLED_TERMINATED) {
			return entry(ACTIVITY_TYPE_RECONCILIATION, null, properties.process().settledTerminated(), SEVERITY_ERROR, ERROR_CODE_TERMINATED, message, orNow(occurredAt));
		}
		return entry(ACTIVITY_TYPE_RECONCILIATION, null, properties.process().settledCompleted(), SEVERITY_WARN, null, message, orNow(occurredAt));
	}

	private String nameOf(final ExternalTask externalTask, final Outcome outcome) {
		final var activityId = externalTask.getActivityId();
		return stepText(activityId, outcome)
			.orElseGet(() -> orId(operatonIntegration.labelOf(externalTask.getProcessDefinitionId(), activityId), activityId));
	}

	private Optional<String> stepText(final String activityId, final Outcome outcome) {
		return Optional.ofNullable(properties.steps().get(activityId))
			.map(texts -> switch (outcome)
			{
				case DONE -> texts.done();
				case RETRY -> texts.retry();
				case FAILED -> texts.failed();
				case SKIPPED -> texts.skipped();
				default -> null;
			});
	}

	private static String orId(final String name, final String id) {
		return Optional.ofNullable(name).orElse(id);
	}

	private static ProcessActivity entry(final String type, final String activityId, final String name, final String severity, final String errorCode,
		final String message, final OffsetDateTime occurredAt) {
		return new ProcessActivity()
			.activityType(type)
			.activityId(abbreviate(activityId, MAX_ID_LENGTH))
			.activityName(abbreviate(name, MAX_NAME_LENGTH))
			.severity(severity)
			.errorCode(abbreviate(errorCode, MAX_ERROR_CODE_LENGTH))
			.message(abbreviate(message, MAX_MESSAGE_LENGTH))
			.occurredAt(occurredAt);
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(UTC);
	}

	/** Support Management requires occurredAt, but the engine fields it is read from are nullable. */
	private static OffsetDateTime orNow(final OffsetDateTime occurredAt) {
		return Optional.ofNullable(occurredAt).orElseGet(ProcessLog::now);
	}
}
