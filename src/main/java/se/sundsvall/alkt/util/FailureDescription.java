package se.sundsvall.alkt.util;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.ThrowableProblem;

/**
 * A failure described for the process log, the incident and the alert. The text of another service's answer is left
 * out, since there is no telling what it holds; our own messages carry ids only and are kept.
 */
public final class FailureDescription {

	// ClientProblem and ServerProblem only come from the error decoders, whose detail reads
	// "<client-id> error: {detail=..., status=503 Service Unavailable, title=...}".
	private static final Pattern CLIENT_ID = Pattern.compile("^([a-z][a-z-]*) error: ");
	private static final Pattern REMOTE_STATUS = Pattern.compile("status=(\\d{3})");

	private FailureDescription() {}

	public static String describe(final Throwable throwable) {
		return switch (throwable) {
			case final ClientProblem problem -> describeRemote(problem);
			case final ServerProblem problem -> describeRemote(problem);
			case final ThrowableProblem problem -> withText("%s %s".formatted(nameOf(problem), statusOf(problem)), problem.getDetail());
			case final NonRetryableException exception -> withText(nameOf(exception), exception.getMessage());
			default -> nameOf(throwable) + Optional.ofNullable(throwable.getCause())
				.map(cause -> " caused by " + nameOf(cause))
				.orElse("");
		};
	}

	private static String describeRemote(final ThrowableProblem problem) {
		final var detail = Optional.ofNullable(problem.getDetail()).orElse("");
		final var description = new StringBuilder("%s %s".formatted(nameOf(problem), statusOf(problem)));

		final var clientId = CLIENT_ID.matcher(detail);
		if (clientId.find()) {
			description.append(" from ").append(clientId.group(1));
		}
		// The last one, since the remote detail comes first and may itself mention a status.
		final var remoteStatus = REMOTE_STATUS.matcher(detail).results()
			.map(result -> HttpStatus.resolve(Integer.parseInt(result.group(1))))
			.filter(Objects::nonNull)
			.reduce((first, second) -> second);
		remoteStatus.ifPresent(status -> description.append(" (remote %s %s)".formatted(status.value(), status.getReasonPhrase())));

		return description.toString();
	}

	private static String statusOf(final ThrowableProblem problem) {
		return String.valueOf(problem.getStatusCode().value());
	}

	private static String withText(final String prefix, final String text) {
		return Optional.ofNullable(text)
			.map(value -> prefix + ": " + value)
			.orElse(prefix);
	}

	private static String nameOf(final Throwable throwable) {
		return throwable.getClass().getSimpleName();
	}
}
