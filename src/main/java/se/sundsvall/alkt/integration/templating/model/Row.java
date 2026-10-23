package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/** A row of a table field: one cell per column, in the order of the columns. Sent as a plain array of texts. */
public record Row(@JsonValue List<String> cells) {

	public static Row of(final String... cells) {
		return new Row(List.of(cells));
	}
}
