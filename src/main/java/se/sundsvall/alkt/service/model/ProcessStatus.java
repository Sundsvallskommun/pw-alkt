package se.sundsvall.alkt.service.model;

/** The states Support Management keeps a process in. */
public enum ProcessStatus {
	RUNNING,
	WAITING,
	RETRYING,
	COMPLETED,
	FAILED
}
