package se.sundsvall.alkt.service.model;

/** The states Support Management keeps a process in. */
public enum ProcessStatus {
	RUNNING,
	WAITING,
	RETRYING,
	COMPLETED,
	FAILED;

	/** A state the instance does not leave, so there is no wait state after it to report. */
	public boolean isTerminal() {
		return (this == COMPLETED) || (this == FAILED);
	}
}
