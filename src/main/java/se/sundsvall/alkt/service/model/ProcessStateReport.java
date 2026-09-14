package se.sundsvall.alkt.service.model;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.util.List;
import se.sundsvall.alkt.api.model.ProcessStatus;

import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.api.model.ProcessStatus.RETRYING;

/**
 * What a work step tells Support Management about the process once the step is done. Returned by every work step, so a
 * step that reports nothing does not compile. The factories exist so that a step never has to know which statuses count
 * as terminal.
 *
 * @param status              the state the process is in once the step is done
 * @param currentActivityId   the activity the process is at, as the model names it. Optional
 * @param currentActivityName display name of that activity. Optional
 * @param error               why the process failed. Set when the status says it did
 * @param activities          entries for the activity log of the errand, appended in the same call as the state
 */
public record ProcessStateReport(
	ProcessStatus status,
	String currentActivityId,
	String currentActivityName,
	ProcessError error,
	List<ProcessActivity> activities) {

	public ProcessStateReport {
		activities = activities == null ? List.of() : List.copyOf(activities);
	}

	public static ProcessStateReport completed() {
		return new ProcessStateReport(COMPLETED, null, null, null, List.of());
	}

	public static ProcessStateReport failed(final String code, final String message) {
		return new ProcessStateReport(FAILED, null, null, toError(code, message), List.of());
	}

	public static ProcessStateReport retrying(final String code, final String message) {
		return new ProcessStateReport(RETRYING, null, null, toError(code, message), List.of());
	}

	private static ProcessError toError(final String code, final String message) {
		return new ProcessError().code(code).message(message);
	}
}
