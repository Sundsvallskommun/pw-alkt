package se.sundsvall.alkt.util;

import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;

import static se.sundsvall.alkt.util.FailureDescription.describe;

public final class RemoteAnswers {

	private RemoteAnswers() {}

	/**
	 * The answer to the request, or a failure without retry when the service answers with the status, which a retry does
	 * not change. The failure is the message followed by the description of the answer. Any other failure passes as it is.
	 */
	public static <T> T failWithoutRetryOn(final HttpStatus status, final String message, final Supplier<T> request) {
		try {
			return request.get();
		} catch (final ClientProblem e) {
			if (status.equals(e.getStatus())) {
				throw new NonRetryableException("%s: %s".formatted(message, describe(e)), e);
			}
			throw e;
		}
	}
}
