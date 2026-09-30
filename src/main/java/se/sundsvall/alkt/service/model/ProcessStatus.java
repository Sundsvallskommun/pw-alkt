package se.sundsvall.alkt.service.model;

/** The states Support Management keeps a process in. */
public enum ProcessStatus {
	RUNNING,
	WAITING,
	RETRYING,
	COMPLETED,
	FAILED;

	/** A state with no wait state after it to report: the instance has ended, or stands on an incident. */
	public boolean isTerminal() {
		return (this == COMPLETED) || (this == FAILED);
	}

	/**
	 * Every state but COMPLETED: an instance on an incident is FAILED but still listens, for the cancellation among others.
	 */
	public boolean takesSignals() {
		return this != COMPLETED;
	}
}
