package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/** A field of the template alkt.form-pdf: type says which of the other parts it fills. */
@JsonInclude(NON_NULL)
public record Field(
	String label,
	String type,
	String value,
	List<String> values,
	String html,
	List<GroupField> fields,
	List<String> columns,
	List<Row> rows) {

	public static Field value(final String label, final String value) {
		return new Field(label, "value", value, null, null, null, null, null);
	}

	public static Field list(final String label, final List<String> values) {
		return new Field(label, "list", null, values, null, null, null, null);
	}

	public static Field html(final String label, final String html) {
		return new Field(label, "html", null, null, html, null, null, null);
	}

	public static Field group(final String label, final List<GroupField> fields) {
		return new Field(label, "group", null, null, null, fields, null, null);
	}

	public static Field table(final String label, final List<String> columns, final List<Row> rows) {
		return new Field(label, "table", null, null, null, null, columns, rows);
	}
}
