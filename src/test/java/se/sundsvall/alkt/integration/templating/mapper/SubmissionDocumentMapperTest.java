package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.integration.templating.model.GroupEntry;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.integration.templating.model.TableField;
import se.sundsvall.alkt.integration.templating.model.TableField.Row;
import se.sundsvall.alkt.integration.templating.model.ValueField;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toAttachmentSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toErrandSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toFileName;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toStakeholderSection;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toTitle;

class SubmissionDocumentMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void theTitleNamesTheErrandNumber() {
		assertThat(toTitle(new Errand().errandNumber("ALKT-2026-000123"))).isEqualTo("Inkommet ärende ALKT-2026-000123");
	}

	@Test
	void theFileNameIsTheTitleAsAPdf() {
		assertThat(toFileName(new Errand().errandNumber("ALKT-2026-000123"))).isEqualTo("Inkommet ärende ALKT-2026-000123.pdf");
	}

	@Test
	void theErrandSectionShowsTheErrandNumberAndTheDateItCameInInSwedishTime() {
		final var errand = new Errand().errandNumber("ALKT-2026-000123").created(OffsetDateTime.parse("2026-10-21T23:30:00Z"));

		assertThat(toErrandSection(errand)).isEqualTo(new Section("Ärende", List.of(
			new ValueField("Ärendenummer", "ALKT-2026-000123"),
			new ValueField("Inkomstdatum", "2026-10-22"))));
	}

	@Test
	void theErrandSectionKeepsTheDateBeforeMidnightInSwedishTime() {
		final var errand = new Errand().errandNumber("ALKT-2026-000123").created(OffsetDateTime.parse("2026-10-21T21:59:00Z"));

		assertThat(toErrandSection(errand).fields()).contains(new ValueField("Inkomstdatum", "2026-10-21"));
	}

	@Test
	void theStakeholderSectionHasAGroupPerStakeholderLabelledByRole() {
		final var company = new Stakeholder()
			.role("PRIMARY")
			.externalId("party-1")
			.externalIdType("COMPANY")
			.organizationName("Kiosken AB")
			.address("Storgatan 1")
			.zipCode("851 85")
			.city("Sundsvall")
			.contactChannels(List.of(new ContactChannel().type("EMAIL").value("kiosk@example.com"), new ContactChannel().type("PHONE").value("060-123456")));
		final var person = new Stakeholder()
			.role("CONTACT")
			.externalId("party-2")
			.externalIdType("PRIVATE")
			.firstName("Anna")
			.lastName("Andersson")
			.contactChannels(List.of(new ContactChannel().type("EMAIL").value("anna@example.com")));

		final var section = toStakeholderSection(List.of(company, person), Map.of("party-1", "5566124144", "party-2", "199001012385"));

		assertThat(section).contains(new Section("Intressenter", List.of(
			new GroupField("PRIMARY", List.of(
				new GroupEntry("Namn", "Kiosken AB"),
				new GroupEntry("Organisationsnummer", "5566124144"),
				new GroupEntry("Adress", "Storgatan 1, 851 85 Sundsvall"),
				new GroupEntry("EMAIL", "kiosk@example.com"),
				new GroupEntry("PHONE", "060-123456"))),
			new GroupField("CONTACT", List.of(
				new GroupEntry("Namn", "Anna Andersson"),
				new GroupEntry("Personnummer", "199001012385"),
				new GroupEntry("EMAIL", "anna@example.com"))))));
	}

	@Test
	void aStakeholderWhosePartyIsNotKnownIsShownWithoutNumber() {
		final var stakeholder = new Stakeholder().role("PRIMARY").externalId("unknown").externalIdType("COMPANY").organizationName("Kiosken AB");

		assertThat(toStakeholderSection(List.of(stakeholder), Map.of())).contains(new Section("Intressenter", List.of(
			new GroupField("PRIMARY", List.of(new GroupEntry("Namn", "Kiosken AB"))))));
	}

	@Test
	void aStakeholderWithoutAnyDetailsIsLeftOut() {
		final var empty = new Stakeholder().role("CONTACT").externalId("unknown").contactChannels(List.of(new ContactChannel().type("EMAIL").value("")));
		final var named = new Stakeholder().role("PRIMARY").organizationName("Kiosken AB");

		assertThat(toStakeholderSection(List.of(empty, named), Map.of())).contains(new Section("Intressenter", List.of(
			new GroupField("PRIMARY", List.of(new GroupEntry("Namn", "Kiosken AB"))))));
	}

	@Test
	void anErrandWithoutStakeholdersWithDetailsHasNoStakeholderSection() {
		assertThat(toStakeholderSection(List.of(), Map.of())).isEmpty();
		assertThat(toStakeholderSection(List.of(new Stakeholder().role("PRIMARY")), Map.of())).isEmpty();
	}

	@Test
	void theAttachmentTableShowsTheFileNameAndTheLabelOfTheMatchingAttachmentOfTheSchema() {
		final var schema = JSON.readTree("""
			{"x-attachments": [{"key": "POWER_OF_ATTORNEY", "label": "Fullmakt"}, {"key": "SELF_MONITORING_PROGRAM", "label": "Egenkontrollprogram"}]}
			""");
		final var attachments = List.of(
			new ErrandAttachment().fileName("program.pdf").purpose(new ErrandAttachmentPurpose().name("SELF_MONITORING_PROGRAM").displayName("Program")),
			new ErrandAttachment().fileName("fullmakt.pdf").purpose(new ErrandAttachmentPurpose().name("POWER_OF_ATTORNEY")));

		assertThat(toAttachmentSection(attachments, schema)).contains(new Section("Bilagor", List.of(
			new TableField("Bilagor", List.of("Filnamn", "Typ"), List.of(
				Row.of("program.pdf", "Egenkontrollprogram"),
				Row.of("fullmakt.pdf", "Fullmakt"))))));
	}

	@Test
	void anAttachmentWhosePurposeIsNotInTheSchemaShowsTheDisplayNameOfThePurposeAndWithoutPurposeTheTypeIsEmpty() {
		final var schema = JSON.readTree("""
			{"x-attachments": [{"key": "POWER_OF_ATTORNEY", "label": "Fullmakt"}]}
			""");
		final var attachments = List.of(
			new ErrandAttachment().fileName("svar.pdf").purpose(new ErrandAttachmentPurpose().name("RESPONSE").displayName("Inkommen handling")),
			new ErrandAttachment().fileName("okand.pdf").purpose(new ErrandAttachmentPurpose().name("OTHER")),
			new ErrandAttachment().fileName("bild.png"));

		assertThat(toAttachmentSection(attachments, schema)).contains(new Section("Bilagor", List.of(
			new TableField("Bilagor", List.of("Filnamn", "Typ"), List.of(
				Row.of("svar.pdf", "Inkommen handling"),
				Row.of("okand.pdf", ""),
				Row.of("bild.png", ""))))));
	}

	@Test
	void anErrandWithoutAttachmentsHasNoAttachmentSection() {
		assertThat(toAttachmentSection(List.of(), JSON.readTree("{}"))).isEmpty();
	}
}
