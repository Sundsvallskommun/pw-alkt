package se.sundsvall.alkt.exception;

/** A step with nothing to do for this errand: the model takes the process past it and the log says it was skipped. */
public class StepSkippedException extends RuntimeException {

	public StepSkippedException(final String message) {
		super(message);
	}
}
