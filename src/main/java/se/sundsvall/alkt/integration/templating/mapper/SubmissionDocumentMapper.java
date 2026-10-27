package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;
import se.sundsvall.alkt.integration.templating.model.Field;
import se.sundsvall.alkt.integration.templating.model.GroupEntry;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.integration.templating.model.TableField;
import se.sundsvall.alkt.integration.templating.model.TableField.Row;
import se.sundsvall.alkt.integration.templating.model.ValueField;
import tools.jackson.databind.JsonNode;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

/**
 * The title and file name of the submission PDF, and its sections of the errand, its stakeholders and its attachments.
 */
public final class SubmissionDocumentMapper {

	private static final String SEPARATOR = ", ";
	private static final String STAKEHOLDER_TYPE_PRIVATE = "PRIVATE";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");

	private SubmissionDocumentMapper() {}

	public static String toTitle(final Errand errand) {
		return "Inkommet ärende " + errand.getErrandNumber();
	}

	public static String toFileName(final Errand errand) {
		return toTitle(errand) + ".pdf";
	}

	public static Section toErrandSection(final Errand errand) {
		final var fields = new ArrayList<Field>();
		fields.add(new ValueField("Ärendenummer", errand.getErrandNumber()));
		Optional.ofNullable(errand.getCreated())
			.map(created -> created.atZoneSameInstant(SWEDISH_TIME).format(DateTimeFormatter.ISO_LOCAL_DATE))
			.ifPresent(date -> fields.add(new ValueField("Inkomstdatum", date)));
		return new Section("Ärende", fields);
	}

	public static Optional<Section> toStakeholderSection(final List<Stakeholder> stakeholders, final Map<String, String> legalIds) {
		final var fields = stakeholders.stream()
			.map(stakeholder -> new GroupField(Optional.ofNullable(stakeholder.getRole()).orElse(""), toStakeholderFields(stakeholder, legalIds)))
			.filter(field -> !field.fields().isEmpty())
			.map(Field.class::cast)
			.toList();
		return Optional.of(fields).filter(not(List::isEmpty)).map(list -> new Section("Intressenter", list));
	}

	public static Optional<Section> toAttachmentSection(final List<ErrandAttachment> attachments, final JsonNode schema) {
		if (attachments.isEmpty()) {
			return Optional.empty();
		}
		final var rows = attachments.stream()
			.map(attachment -> Row.of(Optional.ofNullable(attachment.getFileName()).orElse(""), typeOf(attachment, schema)))
			.toList();
		return Optional.of(new Section("Bilagor", List.of(new TableField("Bilagor", List.of("Filnamn", "Typ"), rows))));
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

	private static List<GroupEntry> toStakeholderFields(final Stakeholder stakeholder, final Map<String, String> legalIds) {
		final var name = Optional.ofNullable(stakeholder.getOrganizationName()).filter(not(String::isBlank))
			.orElseGet(() -> joinNonBlank(" ", stakeholder.getFirstName(), stakeholder.getLastName()));
		final var nameEntry = new GroupEntry("Namn", name);
		final var legalIdEntry = new GroupEntry(STAKEHOLDER_TYPE_PRIVATE.equals(stakeholder.getExternalIdType()) ? "Personnummer" : "Organisationsnummer",
			Optional.ofNullable(stakeholder.getExternalId()).map(legalIds::get).orElse(null));
		final var addressEntry = new GroupEntry("Adress",
			joinNonBlank(SEPARATOR, stakeholder.getCareOf(), stakeholder.getAddress(), joinNonBlank(" ", stakeholder.getZipCode(), stakeholder.getCity())));
		final var channelEntries = Optional.ofNullable(stakeholder.getContactChannels()).orElse(emptyList()).stream()
			.map(channel -> new GroupEntry(Optional.ofNullable(channel.getType()).orElse(""), channel.getValue()));

		return Stream.concat(Stream.of(nameEntry, legalIdEntry, addressEntry), channelEntries)
			.filter(entry -> StringUtils.hasText(entry.value()))
			.toList();
	}

	private static String joinNonBlank(final String separator, final String... parts) {
		return Arrays.stream(parts).filter(StringUtils::hasText).collect(Collectors.joining(separator));
	}
}
