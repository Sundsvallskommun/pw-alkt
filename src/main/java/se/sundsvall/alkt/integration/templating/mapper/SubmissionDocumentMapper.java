package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;
import se.sundsvall.alkt.integration.templating.model.Field;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.Section;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

/**
 * Turns the answers of a form into the sections of the template alkt.form-pdf, laid out as the UI schema lays out the
 * form and labelled by the schema.
 */
public final class SubmissionDocumentMapper {

	private static final String TEXT_EDITOR_WIDGET = "TexteditorWidget";
	private static final String SEPARATOR = ", ";
	private static final String STAKEHOLDER_TYPE_PRIVATE = "PRIVATE";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");

	private static final Pattern TIME = Pattern.compile("^(\\d{2}:\\d{2})(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})?$");

	// The template writes html unescaped, so only bare formatting tags survive: no images, links, styles or attributes that
	// could make templating fetch an outside address.
	private static final Set<String> ALLOWED_TAGS = Set.of("p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li", "blockquote",
		"h1", "h2", "h3", "h4", "h5", "h6", "sub", "sup");
	private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");
	private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)(?:\\s[^<>]*)?/?>");
	private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)");

	private SubmissionDocumentMapper() {}

	public static String toTitle(final Errand errand) {
		return "Inkommen anmälan " + errand.getErrandNumber();
	}

	public static String toFileName(final Errand errand) {
		return toTitle(errand) + ".pdf";
	}

	public static Section toErrandSection(final Errand errand) {
		final var fields = new ArrayList<Field>();
		fields.add(Field.value("Ärendenummer", errand.getErrandNumber()));
		Optional.ofNullable(errand.getCreated())
			.map(created -> created.atZoneSameInstant(SWEDISH_TIME).format(DateTimeFormatter.ISO_LOCAL_DATE))
			.ifPresent(date -> fields.add(Field.value("Inkomstdatum", date)));
		return new Section("Ärende", fields);
	}

	public static Optional<Section> toStakeholderSection(final List<Stakeholder> stakeholders, final Map<String, String> legalIds) {
		final var fields = stakeholders.stream()
			.map(stakeholder -> Field.group(Optional.ofNullable(stakeholder.getRole()).orElse(""), toStakeholderFields(stakeholder, legalIds)))
			.filter(field -> !field.fields().isEmpty())
			.toList();
		return Optional.of(fields).filter(not(List::isEmpty)).map(list -> new Section("Intressenter", list));
	}

	public static Optional<Section> toAttachmentSection(final List<ErrandAttachment> attachments, final JsonNode schema) {
		if (attachments.isEmpty()) {
			return Optional.empty();
		}
		final var rows = attachments.stream()
			.map(attachment -> List.of(Optional.ofNullable(attachment.getFileName()).orElse(""), typeOf(attachment, schema)))
			.toList();
		return Optional.of(new Section("Bilagor", List.of(Field.table("Bilagor", List.of("Filnamn", "Typ"), rows))));
	}

	private static String typeOf(final ErrandAttachment attachment, final JsonNode schema) {
		return Optional.ofNullable(attachment.getPurpose())
			.map(purpose -> schema.path("x-attachments").values().stream()
				.filter(candidate -> candidate.path("key").asString("").equals(purpose.getName()))
				.map(candidate -> candidate.path("label").asString(null))
				.filter(Objects::nonNull)
				.findFirst()
				.or(() -> Optional.ofNullable(purpose.getDisplayName()))
				.orElse(""))
			.orElse("");
	}

	private static List<GroupField> toStakeholderFields(final Stakeholder stakeholder, final Map<String, String> legalIds) {
		final var fields = new ArrayList<GroupField>();
		final var name = Optional.ofNullable(stakeholder.getOrganizationName()).filter(not(String::isBlank))
			.orElseGet(() -> joinNonBlank(" ", stakeholder.getFirstName(), stakeholder.getLastName()));
		addIfPresent(fields, "Namn", name);
		Optional.ofNullable(stakeholder.getExternalId()).map(legalIds::get)
			.ifPresent(legalId -> addIfPresent(fields, STAKEHOLDER_TYPE_PRIVATE.equals(stakeholder.getExternalIdType()) ? "Personnummer" : "Organisationsnummer", legalId));
		addIfPresent(fields, "Adress", joinNonBlank(SEPARATOR, stakeholder.getCareOf(), stakeholder.getAddress(), joinNonBlank(" ", stakeholder.getZipCode(), stakeholder.getCity())));
		Optional.ofNullable(stakeholder.getContactChannels()).orElse(emptyList())
			.forEach(channel -> addIfPresent(fields, Optional.ofNullable(channel.getType()).orElse(""), channel.getValue()));
		return fields;
	}

	private static void addIfPresent(final List<GroupField> fields, final String label, final String value) {
		if (StringUtils.hasText(value)) {
			fields.add(new GroupField(label, value));
		}
	}

	private static String joinNonBlank(final String separator, final String... parts) {
		return Arrays.stream(parts).filter(StringUtils::hasText).collect(Collectors.joining(separator));
	}

	/** The sections of the UI schema, then a last section without a title for the answered fields no section lists. */
	public static List<Section> toSections(final JsonNode schema, final JsonNode uiSchema, final JsonNode answers) {
		final var sections = uiSchema.path("ui:sections").values().stream()
			.map(section -> new Section(section.path("title").asString(""),
				toFields(section.path("fields").values().stream().map(JsonNode::asString).toList(), schema, uiSchema, answers)));
		final var listed = uiSchema.path("ui:sections").values().stream()
			.flatMap(section -> section.path("fields").values().stream())
			.map(JsonNode::asString)
			.collect(Collectors.toSet());
		final var leftover = new Section("", toFields(orderOf(schema, uiSchema, null).stream().filter(not(listed::contains)).toList(), schema, uiSchema, answers));
		return Stream.concat(sections, Stream.of(leftover))
			.filter(section -> !section.fields().isEmpty())
			.toList();
	}

	private static List<Field> toFields(final List<String> names, final JsonNode schema, final JsonNode uiSchema, final JsonNode answers) {
		return names.stream()
			.map(name -> toField(name, schema.path("properties").path(name), uiSchema.path(name), answers.path(name)))
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<Field> toField(final String name, final JsonNode schema, final JsonNode uiSchema, final JsonNode answer) {
		if (isEmpty(answer)) {
			return Optional.empty();
		}
		final var label = labelOf(name, schema);
		if (answer.isArray()) {
			return Optional.of(toArrayField(label, schema.path("items"), uiSchema.path("items"), answer));
		}
		if (answer.isObject()) {
			final var fields = toGroupFields(schema, uiSchema, answer);
			if (fields.isEmpty()) {
				return Optional.empty();
			}
			if (fields.size() == 1) {
				return Optional.of(Field.value(label, fields.getFirst().value()));
			}
			return Optional.of(Field.group(label, fields));
		}
		if (answer.isString() && TEXT_EDITOR_WIDGET.equals(uiSchema.path("ui:widget").asString(null))) {
			return Optional.of(Field.html(label, sanitize(answer.asString())));
		}
		return Optional.of(Field.value(label, toText(answer, schema)));
	}

	private static Field toArrayField(final String label, final JsonNode itemSchema, final JsonNode itemUiSchema, final JsonNode answer) {
		final var items = answer.values().stream().filter(not(SubmissionDocumentMapper::isEmpty)).toList();
		if (items.stream().anyMatch(JsonNode::isObject) || "object".equals(itemSchema.path("type").asString(null))) {
			final var columns = orderOf(itemSchema, itemUiSchema, null);
			return Field.table(label,
				columns.stream().map(column -> labelOf(column, itemSchema.path("properties").path(column))).toList(),
				items.stream().map(item -> columns.stream()
					.map(column -> Optional.of(item.path(column)).filter(not(SubmissionDocumentMapper::isEmpty))
						.map(value -> toText(value, itemSchema.path("properties").path(column)))
						.orElse(""))
					.toList())
					.toList());
		}
		return Field.list(label, items.stream().map(item -> toText(item, itemSchema)).toList());
	}

	// Why: the template shows one level of fields in a group, so a deeper object is flattened into the group.
	private static List<GroupField> toGroupFields(final JsonNode schema, final JsonNode uiSchema, final JsonNode answer) {
		final var fields = new ArrayList<GroupField>();
		for (final var key : orderOf(schema, uiSchema, answer)) {
			final var value = answer.path(key);
			if (isEmpty(value)) {
				continue;
			}
			final var fieldSchema = schema.path("properties").path(key);
			if (value.isObject()) {
				fields.addAll(toGroupFields(fieldSchema, uiSchema.path(key), value));
			} else {
				fields.add(new GroupField(labelOf(key, fieldSchema), toText(value, fieldSchema)));
			}
		}
		return fields;
	}

	/** The keys of an object in the order of its ui:order, then those the schema or the answer holds besides. */
	private static List<String> orderOf(final JsonNode schema, final JsonNode uiSchema, final JsonNode answer) {
		final var keys = new LinkedHashSet<String>();
		uiSchema.path("ui:order").values().stream().map(JsonNode::asString).filter(not("*"::equals)).forEach(keys::add);
		keys.addAll(schema.path("properties").propertyNames());
		Optional.ofNullable(answer).ifPresent(value -> keys.addAll(value.propertyNames()));
		return List.copyOf(keys);
	}

	private static String toText(final JsonNode value, final JsonNode schema) {
		if (value.isArray()) {
			return value.values().stream().filter(not(SubmissionDocumentMapper::isEmpty)).map(item -> toText(item, schema.path("items")))
				.collect(Collectors.joining(SEPARATOR));
		}
		if (value.isObject()) {
			return toGroupFields(schema, MissingNode.getInstance(), value).stream().map(GroupField::value).collect(Collectors.joining(SEPARATOR));
		}
		return schema.path("oneOf").values().stream()
			.filter(option -> value.equals(option.path("const")))
			.map(option -> option.path("title").asString(null))
			.filter(Objects::nonNull)
			.findFirst()
			.orElseGet(() -> toTime(value.asString()));
	}

	private static String toTime(final String text) {
		final var matcher = TIME.matcher(text);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return text;
	}

	private static String labelOf(final String name, final JsonNode schema) {
		return schema.path("title").asString(name);
	}

	private static boolean isEmpty(final JsonNode value) {
		return value.isMissingNode() || value.isNull() || (value.isString() && value.asString().isBlank()) || (value.isContainer() && value.isEmpty());
	}

	private static String sanitize(final String html) {
		final var text = SCRIPT_OR_STYLE.matcher(html).replaceAll("");
		final var sanitized = new StringBuilder();
		final var matcher = TAG.matcher(text);
		var last = 0;
		while (matcher.find()) {
			sanitized.append(escape(text.substring(last, matcher.start())));
			final var tag = matcher.group(2).toLowerCase(Locale.ROOT);
			if ("br".equals(tag)) {
				sanitized.append("<br/>");
			} else if (ALLOWED_TAGS.contains(tag)) {
				sanitized.append('<').append(matcher.group(1)).append(tag).append('>');
			}
			last = matcher.end();
		}
		return sanitized.append(escape(text.substring(last))).toString();
	}

	private static String escape(final String text) {
		return BARE_AMPERSAND.matcher(text).replaceAll("&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
