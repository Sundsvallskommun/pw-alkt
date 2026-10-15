package se.sundsvall.alkt.service.model;

/** The action errand of an inspection, or why the case worker has to complete the inspection before one is created. */
public record ActionErrandResult(String actionErrandId, String refusal) {

	public static ActionErrandResult created(final String actionErrandId) {
		return new ActionErrandResult(actionErrandId, null);
	}

	public static ActionErrandResult refused(final String refusal) {
		return new ActionErrandResult(null, refusal);
	}
}
