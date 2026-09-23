package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ATTACHMENT_PART_NAME;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ORIGIN;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPartyId;

class PartyAssetsMapperTest {

	private static final String ERRAND_ID = randomUUID().toString();
	private static final String PARTY_ID = randomUUID().toString();

	@Test
	void toAssetCreateRequestCarriesTheDecision() {
		final var decision = new Decision()
			.id(randomUUID().toString())
			.type("PERMIT")
			.title("Beslut om serveringstillstånd")
			.validFrom(LocalDate.of(2026, 10, 1))
			.validTo(LocalDate.of(2027, 9, 30))
			.decidedAt(OffsetDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneOffset.UTC))
			.legalBasis("8 kap. 12 § alkohollagen")
			.delegationReference("3.2.1");

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID);

		assertThat(result.getAssetId()).isEqualTo(decision.getId());
		assertThat(result.getOrigin()).isEqualTo(ORIGIN);
		assertThat(result.getPartyId()).isEqualTo(PARTY_ID);
		assertThat(result.getType()).isEqualTo("PERMIT");
		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(result.getDescription()).isEqualTo("Beslut om serveringstillstånd");
		assertThat(result.getStatus()).isEqualTo(DRAFT);
		assertThat(result.getAdditionalParameters()).containsExactly(
			entry(PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PARAMETER_LEGAL_BASIS, "8 kap. 12 § alkohollagen"),
			entry(PARAMETER_DELEGATION_REFERENCE, "3.2.1"));
	}

	@Test
	void toAssetCreateRequestIssuesOnTheDayOfTheDecisionWithoutValidFrom() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 20, 23, 30, 0, 0, ZoneOffset.ofHours(2)));

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID);

		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
		assertThat(result.getAdditionalParameters()).containsExactly(entry(PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestIssuesOnTheSwedishDayOfADecisionMadeJustAfterMidnight() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 19, 22, 30, 0, 0, ZoneOffset.UTC));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void toAssetCreateRequestIssuesOnTheSwedishDayInWinterTime() {
		final var justBeforeMidnight = new Decision().decidedAt(OffsetDateTime.of(2026, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC));
		final var justAfterMidnight = new Decision().decidedAt(OffsetDateTime.of(2026, 1, 14, 23, 30, 0, 0, ZoneOffset.UTC));

		assertThat(toAssetCreateRequest(justBeforeMidnight, ERRAND_ID, PARTY_ID).getIssued()).isEqualTo(LocalDate.of(2026, 1, 14));
		assertThat(toAssetCreateRequest(justAfterMidnight, ERRAND_ID, PARTY_ID).getIssued()).isEqualTo(LocalDate.of(2026, 1, 15));
	}

	@Test
	void toAssetCreateRequestLeavesIssuedOutWithoutAnyDate() {
		assertThat(toAssetCreateRequest(new Decision(), ERRAND_ID, PARTY_ID).getIssued()).isNull();
	}

	@Test
	void toAssetFileCarriesTheFileAndTakesTheCategoryFromThePurpose() throws IOException {
		final var content = "file".getBytes();
		final var attachment = new ErrandAttachment()
			.fileName("lokalritning.pdf")
			.mimeType("application/pdf")
			.purpose(new ErrandAttachmentPurpose().name("LOKALRITNING").displayName("Lokalritning"));

		final var result = toAssetFile(attachment, content);

		assertThat(result.file().getName()).isEqualTo(ATTACHMENT_PART_NAME);
		assertThat(result.file().getOriginalFilename()).isEqualTo("lokalritning.pdf");
		assertThat(result.file().getContentType()).isEqualTo("application/pdf");
		assertThat(result.file().getBytes()).isEqualTo(content);
		assertThat(result.category()).isEqualTo("LOKALRITNING");
	}

	@Test
	void toAssetFileWithoutPurposeHasNoCategory() {
		assertThat(toAssetFile(new ErrandAttachment(), new byte[0]).category()).isNull();
	}

	@Test
	void toPartyIdTakesTheExternalIdOfThePermitHolder() {
		final var errand = new Errand().stakeholders(List.of(
			new Stakeholder().role("CONTACT").externalId("contact-id"),
			new Stakeholder().role(STAKEHOLDER_ROLE_PERMIT_HOLDER),
			new Stakeholder().role(STAKEHOLDER_ROLE_PERMIT_HOLDER).externalId("holder-id")));

		assertThat(toPartyId(errand)).contains("holder-id");
	}

	@Test
	void toPartyIdIsEmptyWithoutStakeholders() {
		assertThat(toPartyId(new Errand().stakeholders(null))).isEmpty();
	}
}
