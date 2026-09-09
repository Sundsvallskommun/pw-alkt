package se.sundsvall.alkt.api.model;

import java.util.List;
import java.util.Map;

import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.api.model.ProcessStatus.RETRYING;
import static se.sundsvall.alkt.api.model.ProcessStatus.RUNNING;
import static se.sundsvall.alkt.api.model.ProcessStatus.WAITING;

/** What a step reports back; returning it is what makes reporting unskippable. */
public record ProcessStateReport(
	ProcessStatus status,
	String currentActivityId,
	String currentActivityName,
	Long errandVersion,
	ProcessError error,
	List<ProcessActivity> activities,
	Map<String, Object> variables) {

	private static ProcessStateReport of(final ProcessStatus status, final String activityId, final String activityName, final ProcessError error) {
		return new ProcessStateReport(status, activityId, activityName, null, error, List.of(), Map.of());
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
		return of(FAILED, null, null, new ProcessError(code, message));
	}

	public static ProcessStateReport retrying(final String code, final String message) {
		return of(RETRYING, null, null, new ProcessError(code, message));
	}

	public ProcessStateReport withErrandVersion(final Long errandVersion) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, variables);
	}

	public ProcessStateReport withVariables(final Map<String, Object> variables) {
		return new ProcessStateReport(status, currentActivityId, currentActivityName, errandVersion, error, activities, variables);
	}
}
