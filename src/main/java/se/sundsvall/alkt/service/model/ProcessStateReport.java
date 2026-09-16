package se.sundsvall.alkt.service.model;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.service.model.ProcessStatus.RETRYING;
import static se.sundsvall.alkt.service.model.ProcessStatus.RUNNING;
import static se.sundsvall.alkt.service.model.ProcessStatus.WAITING;

/**
 * What a work step tells Support Management; returning it is what makes reporting unskippable. Use the factories, they
 * know which statuses are terminal. errandVersion is the version a read-only step saw, awaitingSignals what a wait
 * state report carries, and variables what the step hands back to the engine when the task completes.
 */
public record ProcessStateReport(
	ProcessStatus status,
	String currentActivityId,
	String currentActivityName,
	Long errandVersion,
	ProcessError error,
	List<ProcessActivity> activities,
	List<AwaitingSignal> awaitingSignals,
	Map<String, Object> variables) {

	// The lengths Support Management accepts. A longer value is answered with 400, which the failure handler would swallow
	// and leave the row in the wrong state, so the report is cut here instead.
	private static final int MAX_ACTIVITY_LENGTH = 255;
	private static final int MAX_ERROR_CODE_LENGTH = 64;
	private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;

	public ProcessStateReport {
		currentActivityId = abbreviate(currentActivityId, MAX_ACTIVITY_LENGTH);
		currentActivityName = abbreviate(currentActivityName, MAX_ACTIVITY_LENGTH);
		activities = List.copyOf(requireNonNullElse(activities, List.of()));
		awaitingSignals = List.copyOf(requireNonNullElse(awaitingSignals, List.of()));
		variables = Map.copyOf(requireNonNullElse(variables, Map.of()));
	}

	private static ProcessStateReport of(final ProcessStatus status, final String activityId, final String activityName, final ProcessError error) {
		return new ProcessStateReport(status, activityId, activityName, null, error, null, null, null);
	}

	public static ProcessStateReport running(final String activityId, final String activityName) {
		return of(RUNNING, activityId, activityName, null);
	}

	public static ProcessStateReport waiting(final String activityId, final String activityName) {
		return of(WAITING, activityId, activityName, null);
	}

	public static ProcessStateReport completed() {
		return of(COMPLETED, null, null, null);
	}

	public static ProcessStateReport failed(final String code, final String message) {
		return of(FAILED, null, null, toError(code, message));
	}

	public static ProcessStateReport retrying(final String code, final String message) {
		return of(RETRYING, null, null, toError(code, message));
	}

	public ProcessStateReport atActivity(final String activityId) {
		return new ProcessStateReport(status, activityId, currentActivityName, errandVersion, error, activities, awaitingSignals, variables);
	}

	public ProcessStateReport withErrandVersion(final Long errandVersion) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, awaitingSignals, variables);
	}

	public ProcessStateReport withActivities(final List<ProcessActivity> activities) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, awaitingSignals, variables);
	}

	public ProcessStateReport withAwaitingSignals(final List<AwaitingSignal> awaitingSignals) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, awaitingSignals, variables);
	}

	public ProcessStateReport withVariables(final Map<String, Object> variables) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, awaitingSignals, variables);
	}

	private static ProcessError toError(final String code, final String message) {
		return new ProcessError()
			.code(abbreviate(code, MAX_ERROR_CODE_LENGTH))
			.message(abbreviate(message, MAX_ERROR_MESSAGE_LENGTH));
	}
}
