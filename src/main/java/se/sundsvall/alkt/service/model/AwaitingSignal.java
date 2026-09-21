package se.sundsvall.alkt.service.model;

import static org.apache.commons.lang3.StringUtils.abbreviate;

/** The name is the message the engine waits for, the label what the model calls the catch event. */
public record AwaitingSignal(String name, String label) {

	// The lengths Support Management accepts, cut here for the same reason ProcessStateReport cuts the error fields.
	private static final int MAX_NAME_LENGTH = 128;
	private static final int MAX_LABEL_LENGTH = 255;

	public AwaitingSignal {
		name = abbreviate(name, MAX_NAME_LENGTH);
		label = abbreviate(label, MAX_LABEL_LENGTH);
	}
}
