package se.sundsvall.alkt.service.model;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessError;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.service.model.ProcessStatus.RETRYING;

/**
 * What a work step tells Support Management once it is done. Use the factories, they know which statuses are terminal.
 */
public record ProcessStateReport(
	ProcessStatus status,
	String currentActivityId,
	String currentActivityName,
	ProcessError error,
	List<ProcessActivity> activities) {

	// The lengths Support Management accepts. A longer value is answered with 400, which the failure handler would swallow
	// and leave the row in the wrong state, so the report is cut here instead.
	private static final int MAX_ERROR_CODE_LENGTH = 64;
	private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;

	public ProcessStateReport {
		activities = List.copyOf(requireNonNullElse(activities, List.of()));
	}

	public static ProcessStateReport completed() {
		return new ProcessStateReport(COMPLETED, null, null, null, null);
	}

	public static ProcessStateReport failed(final String code, final String message) {
		return new ProcessStateReport(FAILED, null, null, toError(code, message), null);
	}

	public static ProcessStateReport retrying(final String code, final String message) {
		return new ProcessStateReport(RETRYING, null, null, toError(code, message), null);
	}

	public ProcessStateReport atActivity(final String activityId) {
		return new ProcessStateReport(status, activityId, currentActivityName, error, activities);
	}

	private static ProcessError toError(final String code, final String message) {
		return new ProcessError()
			.code(abbreviate(code, MAX_ERROR_CODE_LENGTH))
			.message(abbreviate(message, MAX_ERROR_MESSAGE_LENGTH));
	}
}
