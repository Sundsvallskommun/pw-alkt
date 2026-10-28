package se.sundsvall.alkt.integration.templating.mapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import se.sundsvall.alkt.integration.templating.model.Field;
import se.sundsvall.alkt.integration.templating.model.GroupEntry;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.HtmlField;
import se.sundsvall.alkt.integration.templating.model.ListField;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.integration.templating.model.TableField;
import se.sundsvall.alkt.integration.templating.model.TableField.Row;
import se.sundsvall.alkt.integration.templating.model.ValueField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import static java.util.function.Predicate.not;
import static se.sundsvall.alkt.integration.templating.mapper.HtmlSanitizer.sanitize;
import static se.sundsvall.alkt.integration.templating.mapper.HtmlSanitizer.toPlainText;

/**
 * Turns the answers of a form into the sections of the template alkt.form-pdf, laid out as the UI schema lays out the
 * form and labelled by the schema.
 */
public final class FormSectionMapper {

	private static final String TITLE = "title";
	private static final String ITEMS = "items";
	private static final String TEXT_EDITOR_WIDGET = "TexteditorWidget";
	private static final String SEPARATOR = ", ";

	private static final Pattern TIME = Pattern.compile("^(\\d{2}:\\d{2})(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})?$");

	private FormSectionMapper() {}

	/** The sections of the UI schema, then a last section without a title for the answered fields no section lists. */
	public static List<Section> toSections(final JsonNode schema, final JsonNode uiSchema, final JsonNode answers) {
		final var form = new FormNode("", schema, uiSchema, answers);
		final var uiSections = uiSchema.path("ui:sections").values();
		final var listed = uiSections.stream()
			.flatMap(section -> fieldNamesOf(section).stream())
			.collect(Collectors.toSet());
		final var unlisted = form.schemaAndAnswerKeys().stream().filter(not(listed::contains)).toList();

		return Stream.concat(
			uiSections.stream().map(section -> Map.entry(section.path(TITLE).asString(""), fieldNamesOf(section))),
			Stream.of(Map.entry("", unlisted)))
			.map(layout -> new Section(layout.getKey(), layout.getValue().stream()
				.map(form::child)
				.filter(not(FormNode::isUnanswered))
				.map(FormSectionMapper::toField)
				.toList()))
			.filter(section -> !section.fields().isEmpty())
			.toList();
	}

	private static List<String> fieldNamesOf(final JsonNode uiSection) {
		return uiSection.path("fields").values().stream().map(JsonNode::asString).toList();
	}

	private static Field toField(final FormNode node) {
		if (node.isTable()) {
			return toTable(node);
		}
		if (node.isList()) {
			return toList(node);
		}
		if (node.isGroup()) {
			return toGroup(node);
		}
		if (node.isRichText()) {
			return new HtmlField(node.label(), sanitize(node.answer().asString()));
		}
		return new ValueField(node.label(), toText(node.answer(), node.schema()));
	}

	private static Field toList(final FormNode node) {
		return new ListField(node.label(), answeredValues(node.answer()).stream().map(item -> toText(item, node.items().schema())).toList());
	}

	private static Field toTable(final FormNode node) {
		final var item = node.items();
		final var rows = answeredValues(node.answer()).stream().map(item::withAnswer).toList();
		final var columns = Stream.concat(item.schemaKeys().stream(), rows.stream().flatMap(row -> row.answer().propertyNames().stream()))
			.distinct()
			.toList();
		return new TableField(node.label(),
			columns.stream().map(column -> item.child(column).label()).toList(),
			rows.stream()
				.map(row -> new Row(columns.stream()
					.map(row::child)
					.map(cell -> Optional.of(cell).filter(not(FormNode::isUnanswered)).map(FormSectionMapper::toText).orElse(""))
					.toList()))
				.toList());
	}

	private static Field toGroup(final FormNode node) {
		final var fields = toGroupEntries(node);
		if (fields.size() == 1) {
			return new ValueField(node.label(), fields.getFirst().value());
		}
		return new GroupField(node.label(), fields);
	}

	// Why: the template shows one level of fields in a group, so a deeper object is flattened into the group.
	private static List<GroupEntry> toGroupEntries(final FormNode node) {
		final var fields = new ArrayList<GroupEntry>();
		for (final var key : node.schemaAndAnswerKeys()) {
			final var child = node.child(key);
			if (child.isUnanswered()) {
				continue;
			}
			if (child.answer().isObject()) {
				fields.addAll(toGroupEntries(child));
			} else {
				fields.add(new GroupEntry(child.label(), toText(child)));
			}
		}
		return fields;
	}

	// Why: a group entry or a table cell is a plain value in the template, so rich text there is reduced to its text.
	private static String toText(final FormNode node) {
		if (node.isRichText()) {
			return toPlainText(node.answer().asString());
		}
		return toText(node.answer(), node.schema());
	}

	private static String toText(final JsonNode value, final JsonNode schema) {
		if (value.isArray()) {
			return answeredValues(value).stream().map(item -> toText(item, schema.path(ITEMS))).collect(Collectors.joining(SEPARATOR));
		}
		if (value.isObject()) {
			return toGroupEntries(new FormNode("", schema, MissingNode.getInstance(), value)).stream().map(GroupEntry::value).collect(Collectors.joining(SEPARATOR));
		}
		return schema.path("oneOf").values().stream()
			.filter(option -> value.equals(option.path("const")))
			.map(option -> option.path(TITLE).asString(null))
			.filter(Objects::nonNull)
			.findFirst()
			.orElseGet(() -> Optional.of(TIME.matcher(value.asString())).filter(Matcher::matches).map(matcher -> matcher.group(1)).orElse(value.asString()));
	}

	private static List<JsonNode> answeredValues(final JsonNode container) {
		return container.values().stream().filter(not(FormSectionMapper::isEmpty)).toList();
	}

	/** True also for an array or object whose values, at any depth, are all empty. */
	private static boolean isEmpty(final JsonNode value) {
		return value.isMissingNode() || value.isNull() || (value.isString() && value.asString().isBlank())
			|| (value.isContainer() && value.values().stream().allMatch(FormSectionMapper::isEmpty));
	}

	/** A field of the form, with its schema, UI schema and answer walked together. */
	private record FormNode(String key, JsonNode schema, JsonNode uiSchema, JsonNode answer) {

		FormNode child(final String childKey) {
			return new FormNode(childKey, schema.path("properties").path(childKey), uiSchema.path(childKey), answer.path(childKey));
		}

		FormNode items() {
			return new FormNode(key, schema.path(ITEMS), uiSchema.path(ITEMS), MissingNode.getInstance());
		}

		FormNode withAnswer(final JsonNode newAnswer) {
			return new FormNode(key, schema, uiSchema, newAnswer);
		}

		String label() {
			return schema.path(TITLE).asString(key);
		}

		boolean isUnanswered() {
			return isEmpty(answer);
		}

		boolean isTable() {
			return answer.isArray()
				&& (answeredValues(answer).stream().anyMatch(JsonNode::isObject) || "object".equals(items().schema().path("type").asString(null)));
		}

		boolean isList() {
			return answer.isArray() && !isTable();
		}

		boolean isGroup() {
			return answer.isObject();
		}

		boolean isRichText() {
			return answer.isString() && TEXT_EDITOR_WIDGET.equals(uiSchema.path("ui:widget").asString(null));
		}

		/** The keys in the order of ui:order, then the other properties of the schema. */
		List<String> schemaKeys() {
			final var keys = new LinkedHashSet<String>();
			uiSchema.path("ui:order").values().stream().map(JsonNode::asString).filter(not("*"::equals)).forEach(keys::add);
			keys.addAll(schema.path("properties").propertyNames());
			return List.copyOf(keys);
		}

		List<String> schemaAndAnswerKeys() {
			final var keys = new LinkedHashSet<>(schemaKeys());
			keys.addAll(answer.propertyNames());
			return List.copyOf(keys);
		}
	}
}
