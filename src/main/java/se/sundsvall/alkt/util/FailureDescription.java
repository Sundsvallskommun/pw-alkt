package se.sundsvall.alkt.util;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.ThrowableProblem;

/**
 * A failure described for the process log, the incident and the alert. The text of another service's answer is left
 * out, since there is no telling what it holds, except the name of a template parameter templating missed; our own
 * messages carry ids only and are kept.
 */
public final class FailureDescription {

	// The error decoders write the detail as "<client-id> error: {detail=..., status=503 Service Unavailable, title=...}",
	// keys sorted, so the status they wrote is the one followed by the title or the end.
	private static final Pattern CLIENT_ID = Pattern.compile("^([a-z][a-z-]*) error: ");
	private static final Pattern REMOTE_STATUS_BEFORE_TITLE = Pattern.compile("status=(\\d{3}) [^,}]*, title=");
	private static final Pattern REMOTE_STATUS_LAST = Pattern.compile("status=(\\d{3}) [^,}]*}$");
	// The one piece of another service's text that is kept: the placeholder a decision left empty, which is the case
	// worker's to fix and only an identifier.
	private static final Pattern MISSING_TEMPLATE_PARAMETER = Pattern.compile("^templating error: \\{detail=Missing template parameter '([A-Za-z0-9_]+)'");

	private FailureDescription() {}

	public static String describe(final Throwable throwable) {
		return switch (throwable) {
			case final ClientProblem problem -> describeRemote(problem);
			case final ServerProblem problem -> describeRemote(problem);
			// A decoder answers a status outside 4xx and 5xx with a plain problem, so the detail tells, not the type.
			case final ThrowableProblem problem when isDecoded(problem) -> describeRemote(problem);
			case final ThrowableProblem problem -> withText("%s %s".formatted(nameOf(problem), statusOf(problem)), problem.getDetail());
			case final NonRetryableException exception -> Optional.ofNullable(exception.getMessage()).orElseGet(() -> nameOf(exception));
			// The registration id is our own configuration and names the service the token was for.
			case final ClientAuthorizationException exception -> describeByKind(exception) + " for " + exception.getClientRegistrationId();
			default -> describeByKind(throwable);
		};
	}

	private static String describeByKind(final Throwable throwable) {
		return nameOf(throwable) + Optional.ofNullable(throwable.getCause())
			.map(cause -> " caused by " + nameOf(cause))
			.orElse("");
	}

	private static boolean isDecoded(final ThrowableProblem problem) {
		return problem.getDetail() != null && CLIENT_ID.matcher(problem.getDetail()).find();
	}

	private static String describeRemote(final ThrowableProblem problem) {
		final var detail = Optional.ofNullable(problem.getDetail()).orElse("");
		final var description = new StringBuilder("%s %s".formatted(nameOf(problem), statusOf(problem)));

		final var clientId = CLIENT_ID.matcher(detail);
		if (clientId.find()) {
			description.append(" from ").append(clientId.group(1));
		}
		Stream.of(REMOTE_STATUS_BEFORE_TITLE, REMOTE_STATUS_LAST)
			.flatMap(pattern -> pattern.matcher(detail).results())
			.map(result -> HttpStatus.resolve(Integer.parseInt(result.group(1))))
			.filter(Objects::nonNull)
			.findFirst()
			.ifPresent(status -> description.append(" (remote %s %s)".formatted(status.value(), status.getReasonPhrase())));
		MISSING_TEMPLATE_PARAMETER.matcher(detail).results()
			.findFirst()
			.ifPresent(result -> description.append(", missing template parameter '%s'".formatted(result.group(1))));

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
