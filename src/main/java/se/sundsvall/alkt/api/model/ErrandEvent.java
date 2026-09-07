package se.sundsvall.alkt.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Schema(description = "An event on an errand, published by Support Management")
public class ErrandEvent {

	@Schema(description = "What happened to the errand", requiredMode = REQUIRED)
	public enum EventType {
		CREATE, UPDATE, DELETE
	}

	@Schema(description = "Id of the event, unique per published event", example = "3f2b91c4-7d5e-4a10-9c33-8e6b2f0a1d77", requiredMode = REQUIRED)
	@NotBlank
	private String eventId;

	@Schema(requiredMode = REQUIRED)
	@NotNull
	private EventType eventType;

	@Schema(description = "What kind of change it was, for example ERRAND, MESSAGE, ATTACHMENT, DECISION or SIGNAL", example = "MESSAGE")
	private String eventSubType;

	@Schema(description = "Support Management errand ID", example = "f0882f1d-06bc-47fd-b017-1d8307f5ce95", requiredMode = REQUIRED)
	@ValidUuid
	private String errandId;

	@Schema(description = """
		Key of the process that drives the errand, taken from its running instance when it has one and from the labels of
		the errand otherwise. Absent when an errand is deleted before a key can be resolved.
		""", example = "alcohol-serving")
	private String processKey;

	@Schema(description = """
		Whether this event may start a new process instance. Support Management works it out when the event is published,
		since a process that already ran to its end is invisible to this service. Read as false when absent: a process
		that starts when it should not spends the one process life the errand has, whereas a start that fails to happen
		shows up in the user interface as a button waiting to be pressed.
		""", requiredMode = REQUIRED)
	private Boolean startAllowed;

	@Schema(description = """
		Name of the message to correlate, set only when eventSubType is SIGNAL. It is the name of the gate in the process
		model that a case worker pressed, and the only field that points into the model.
		""", example = "review_completed")
	private String signalName;

	@Schema(description = "When the event occurred", example = "2026-08-19T09:12:03.221+02:00")
	private OffsetDateTime occurredAt;

	public String getEventId() {
		return eventId;
	}

	public void setEventId(final String eventId) {
		this.eventId = eventId;
	}

	public EventType getEventType() {
		return eventType;
	}

	public void setEventType(final EventType eventType) {
		this.eventType = eventType;
	}

	public String getEventSubType() {
		return eventSubType;
	}

	public void setEventSubType(final String eventSubType) {
		this.eventSubType = eventSubType;
	}

	public String getErrandId() {
		return errandId;
	}

	public void setErrandId(final String errandId) {
		this.errandId = errandId;
	}

	public String getProcessKey() {
		return processKey;
	}

	public void setProcessKey(final String processKey) {
		this.processKey = processKey;
	}

	public Boolean getStartAllowed() {
		return startAllowed;
	}

	public void setStartAllowed(final Boolean startAllowed) {
		this.startAllowed = startAllowed;
	}

	public boolean permitsStart() {
		return Boolean.TRUE.equals(startAllowed);
	}

	public String getSignalName() {
		return signalName;
	}

	public void setSignalName(final String signalName) {
		this.signalName = signalName;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(final OffsetDateTime occurredAt) {
		this.occurredAt = occurredAt;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof final ErrandEvent that)) {
			return false;
		}
		return Objects.equals(eventId, that.eventId) && eventType == that.eventType && Objects.equals(eventSubType, that.eventSubType)
			&& Objects.equals(errandId, that.errandId) && Objects.equals(processKey, that.processKey) && Objects.equals(startAllowed, that.startAllowed)
			&& Objects.equals(signalName, that.signalName) && Objects.equals(occurredAt, that.occurredAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(eventId, eventType, eventSubType, errandId, processKey, startAllowed, signalName, occurredAt);
	}

	@Override
	public String toString() {
		return "ErrandEvent{eventId='%s', eventType=%s, eventSubType='%s', errandId='%s', processKey='%s', startAllowed=%s, signalName='%s', occurredAt=%s}"
			.formatted(eventId, eventType, eventSubType, errandId, processKey, startAllowed, signalName, occurredAt);
	}
}
