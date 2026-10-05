package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ATTACHMENT_PART_NAME;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ORIGIN;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetUpdateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;

class PartyAssetsMapperTest {

	private static final String ERRAND_ID = randomUUID().toString();
	private static final String PARTY_ID = randomUUID().toString();

	@Test
	void toAssetCreateRequestCarriesTheDecision() {
		final var decision = new Decision()
			.id(randomUUID().toString())
			.type("PERMIT")
			.title("Beslut om serveringstillstånd")
			.description("Tillstånd för servering till allmänheten")
			.validFrom(LocalDate.of(2026, 10, 1))
			.validTo(LocalDate.of(2027, 9, 30))
			.decidedAt(OffsetDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneOffset.UTC))
			.legalBasis("8 kap. 12 § alkohollagen")
			.delegationReference("3.2.1")
			.parameters(List.of(
				new Parameter().key("serveringstid").values(List.of("Servering får ske mellan 11.00 och 01.00.")),
				new Parameter().key("serveringsyta").values(List.of("Servering får ske i matsalen."))))
			.terms(List.of(new DecisionTerm().category("villkor").text("Ordningsvakt ska finnas efter 23.00.")));

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID);

		assertThat(result.getAssetId()).isEqualTo(decision.getId());
		assertThat(result.getOrigin()).isEqualTo(ORIGIN);
		assertThat(result.getPartyId()).isEqualTo(PARTY_ID);
		assertThat(result.getType()).isEqualTo("PERMIT");
		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(result.getTitle()).isEqualTo("Beslut om serveringstillstånd");
		assertThat(result.getDescription()).isEqualTo("Tillstånd för servering till allmänheten");
		assertThat(result.getStatus()).isEqualTo(DRAFT);
		assertThat(result.getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_LEGAL_BASIS, "8 kap. 12 § alkohollagen"),
			entry(PERMIT_PARAMETER_DELEGATION_REFERENCE, "3.2.1"),
			entry("serveringstid", "Servering får ske mellan 11.00 och 01.00."),
			entry("serveringsyta", "Servering får ske i matsalen."),
			entry(PERMIT_PARAMETER_CONDITIONS, "Ordningsvakt ska finnas efter 23.00."));
	}

	@Test
	void toAssetCreateRequestLeavesOutAParameterWithoutValue() {
		final var decision = new Decision().parameters(List.of(new Parameter().key("serveringstid"), new Parameter().key("serveringsyta").values(List.of(" "))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestLetsAParameterWinOverAKeyOfOurOwn() {
		final var decision = new Decision()
			.legalBasis("8 kap. 12 § alkohollagen")
			.parameters(List.of(new Parameter().key(PERMIT_PARAMETER_LEGAL_BASIS).values(List.of("Angiven av handläggaren."))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_LEGAL_BASIS, "Angiven av handläggaren."));
	}

	@Test
	void toAssetCreateRequestKeepsTheErrandIdOverAParameterOfTheSameKey() {
		final var decision = new Decision().parameters(List.of(new Parameter().key(PERMIT_PARAMETER_ERRAND_ID).values(List.of("another-errand"))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestLetsTheTermsWinOverAParameterNamedConditions() {
		final var decision = new Decision()
			.parameters(List.of(new Parameter().key(PERMIT_PARAMETER_CONDITIONS).values(List.of("Från en parameter."))))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat.")));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_CONDITIONS, "Serveringsområdet ska vara avgränsat."));
	}

	@Test
	void toAssetCreateRequestLeavesOutConditionsWithoutText() {
		final var decision = new Decision().terms(List.of(new DecisionTerm().sortOrder(1).text(" ")));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestJoinsTheValuesOfAParameterLeavingOutBlankOnes() {
		final var decision = new Decision().parameters(List.of(new Parameter().key("serveringsyta").values(List.of("Matsalen", " ", "Uteserveringen"))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID).getAdditionalParameters()).containsEntry("serveringsyta", "Matsalen, Uteserveringen");
	}

	@Test
	void toAssetCreateRequestIssuesOnTheDayOfTheDecisionWithoutValidFrom() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 20, 23, 30, 0, 0, ZoneOffset.ofHours(2)));

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID);

		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
		assertThat(result.getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
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
	void toAssetUpdateRequestPutsTheDecisionOnTopOfTheParametersOfTheAsset() {
		final var current = new Asset().additionalParameters(Map.of(
			PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id",
			"serveringstid", "Servering får ske mellan 11.00 och 01.00.",
			"serveringsyta", "Servering får ske i matsalen."));
		final var decision = new Decision()
			.validTo(LocalDate.of(2027, 9, 30))
			.parameters(List.of(new Parameter().key("serveringstid").values(List.of("Servering får ske mellan 11.00 och 02.00."))));

		final var result = toAssetUpdateRequest(current, decision);

		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(result.getTitle()).isNull();
		assertThat(result.getStatus()).isNull();
		assertThat(result.getJsonParameters()).isNull();
		assertThat(result.getAdditionalParameters()).containsOnly(
			entry(PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id"),
			entry("serveringstid", "Servering får ske mellan 11.00 och 02.00."),
			entry("serveringsyta", "Servering får ske i matsalen."));
	}

	@Test
	void toAssetUpdateRequestLeavesValidToOutWhenTheDecisionHasNone() {
		final var result = toAssetUpdateRequest(new Asset().additionalParameters(null), new Decision());

		assertThat(result.getValidTo()).isNull();
		assertThat(result.getIndefinitely()).isNull();
		assertThat(result.getAdditionalParameters()).isEmpty();
	}

	@Test
	void toAssetUpdateRequestRemovesAKeyTheDecisionGivesNoValue() {
		final var current = new Asset().additionalParameters(Map.of(
			PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id",
			PERMIT_PARAMETER_LEGAL_BASIS, "8 kap. 2 § alkohollagen",
			"serveringstid", "11.00–01.00",
			"uteservering", "Uteservering på torget",
			PERMIT_PARAMETER_CONDITIONS, "Ordningsvakt efter 23.00."));
		final var decision = new Decision().parameters(List.of(
			new Parameter().key("uteservering").values(List.of()),
			new Parameter().key(PERMIT_PARAMETER_ERRAND_ID).values(List.of()),
			new Parameter().key(PERMIT_PARAMETER_LEGAL_BASIS).values(List.of()),
			new Parameter().key(PERMIT_PARAMETER_CONDITIONS).values(List.of(" "))));

		assertThat(toAssetUpdateRequest(current, decision).getAdditionalParameters()).containsOnly(
			entry(PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id"),
			entry(PERMIT_PARAMETER_LEGAL_BASIS, "8 kap. 2 § alkohollagen"),
			entry("serveringstid", "11.00–01.00"),
			entry(PERMIT_PARAMETER_CONDITIONS, "Ordningsvakt efter 23.00."));
	}

	/**
	 * errandId links the permit to the errand that granted it, which neither a change nor a parameter of its decision
	 * replaces.
	 */
	@Test
	void toAssetUpdateRequestKeepsTheErrandIdOfThePermit() {
		final var decision = new Decision().parameters(List.of(new Parameter().key(PERMIT_PARAMETER_ERRAND_ID).values(List.of("from-a-parameter"))));

		assertThat(toAssetUpdateRequest(new Asset().additionalParameters(Map.of(PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id")), decision).getAdditionalParameters())
			.containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id"));
		assertThat(toAssetUpdateRequest(new Asset(), decision).getAdditionalParameters()).isEmpty();
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
		assertThat(result.category()).isEqualTo("Lokalritning");
	}

	@Test
	void toAssetFileWithoutPurposeHasNoCategory() {
		assertThat(toAssetFile(new ErrandAttachment(), new byte[0]).category()).isNull();
	}

	@Test
	void toCertificateFileIsAPdfInTheCertificateCategory() throws IOException {
		final var content = "%PDF-1.7".getBytes();

		final var result = toCertificateFile(content);

		assertThat(result.file().getName()).isEqualTo(ATTACHMENT_PART_NAME);
		assertThat(result.file().getOriginalFilename()).isEqualTo("tillstandsbevis.pdf");
		assertThat(result.file().getContentType()).isEqualTo("application/pdf");
		assertThat(result.file().getBytes()).isEqualTo(content);
		assertThat(result.category()).isEqualTo("Tillståndsbevis");
	}
}
