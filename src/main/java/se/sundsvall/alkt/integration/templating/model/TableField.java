package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

@JsonTypeName("table")
public record TableField(String label, List<String> columns, List<Row> rows)
	implements
	Field {

	/** One cell per column, in the order of the columns. Sent as a plain array of texts. */
	public record Row(@JsonValue List<String> cells) {

		public static Row of(final String... cells) {
			return new Row(List.of(cells));
		}
	}
}
