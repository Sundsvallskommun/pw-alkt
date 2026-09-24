package se.sundsvall.alkt.exception;

/** A failure a retry cannot fix, such as missing configuration. The step goes straight to an incident. */
public class NonRetryableException extends RuntimeException {

	public NonRetryableException(final String message) {
		super(message);
	}
}
