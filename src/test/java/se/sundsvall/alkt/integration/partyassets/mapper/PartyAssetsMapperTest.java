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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.alkt.exception.NonRetryableException;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_ADDITION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_CATERING_OCCASION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_E_CIGARETTE_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CLOSURE;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ATTACHMENT_PART_NAME;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ORIGIN;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_ALCOHOL_SERVING;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_E_CIGARETTE_SALES;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.PERMIT_TYPE_TOBACCO_SALES;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetClosureRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetUpdateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitType;

class PartyAssetsMapperTest {

	private static final String ERRAND_ID = randomUUID().toString();
	private static final String PARTY_ID = randomUUID().toString();
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

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

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null);

		assertThat(result.getAssetId()).isEqualTo(decision.getId());
		assertThat(result.getOrigin()).isEqualTo(ORIGIN);
		assertThat(result.getPartyId()).isEqualTo(PARTY_ID);
		assertThat(result.getType()).isEqualTo(PERMIT_TYPE_ALCOHOL_SERVING);
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

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestLetsAParameterWinOverAKeyOfOurOwn() {
		final var decision = new Decision()
			.legalBasis("8 kap. 12 § alkohollagen")
			.parameters(List.of(new Parameter().key(PERMIT_PARAMETER_LEGAL_BASIS).values(List.of("Angiven av handläggaren."))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_LEGAL_BASIS, "Angiven av handläggaren."));
	}

	@Test
	void toAssetCreateRequestKeepsTheErrandIdOverAParameterOfTheSameKey() {
		final var decision = new Decision().parameters(List.of(new Parameter().key(PERMIT_PARAMETER_ERRAND_ID).values(List.of("another-errand"))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestCarriesTheRestaurantNumberOverAParameterOfTheSameKey() {
		final var decision = new Decision().parameters(List.of(new Parameter().key(PERMIT_PARAMETER_RESTAURANT_NUMBER).values(List.of("from-a-parameter"))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, "22810001").getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_RESTAURANT_NUMBER, "22810001"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void toAssetCreateRequestLeavesOutARestaurantNumberThatIsNotThere(final String restaurantNumber) {
		assertThat(toAssetCreateRequest(new Decision(), ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, restaurantNumber).getAdditionalParameters())
			.containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestLetsTheTermsWinOverAParameterNamedConditions() {
		final var decision = new Decision()
			.parameters(List.of(new Parameter().key(PERMIT_PARAMETER_CONDITIONS).values(List.of("Från en parameter."))))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat.")));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsExactly(
			entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID),
			entry(PERMIT_PARAMETER_CONDITIONS, "Serveringsområdet ska vara avgränsat."));
	}

	@Test
	void toAssetCreateRequestLeavesOutConditionsWithoutText() {
		final var decision = new Decision().terms(List.of(new DecisionTerm().sortOrder(1).text(" ")));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestJoinsTheValuesOfAParameterLeavingOutBlankOnes() {
		final var decision = new Decision().parameters(List.of(new Parameter().key("serveringsyta").values(List.of("Matsalen", " ", "Uteserveringen"))));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getAdditionalParameters()).containsEntry("serveringsyta", "Matsalen, Uteserveringen");
	}

	@Test
	void toAssetCreateRequestIssuesOnTheDayOfTheDecisionWithoutValidFrom() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 20, 23, 30, 0, 0, ZoneOffset.ofHours(2)));

		final var result = toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null);

		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
		assertThat(result.getAdditionalParameters()).containsExactly(entry(PERMIT_PARAMETER_ERRAND_ID, ERRAND_ID));
	}

	@Test
	void toAssetCreateRequestIssuesOnTheSwedishDayOfADecisionMadeJustAfterMidnight() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 19, 22, 30, 0, 0, ZoneOffset.UTC));

		assertThat(toAssetCreateRequest(decision, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void toAssetCreateRequestIssuesOnTheSwedishDayInWinterTime() {
		final var justBeforeMidnight = new Decision().decidedAt(OffsetDateTime.of(2026, 1, 14, 22, 30, 0, 0, ZoneOffset.UTC));
		final var justAfterMidnight = new Decision().decidedAt(OffsetDateTime.of(2026, 1, 14, 23, 30, 0, 0, ZoneOffset.UTC));

		assertThat(toAssetCreateRequest(justBeforeMidnight, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getIssued()).isEqualTo(LocalDate.of(2026, 1, 14));
		assertThat(toAssetCreateRequest(justAfterMidnight, ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getIssued()).isEqualTo(LocalDate.of(2026, 1, 15));
	}

	@Test
	void toAssetCreateRequestLeavesIssuedOutWithoutAnyDate() {
		assertThat(toAssetCreateRequest(new Decision(), ERRAND_ID, PARTY_ID, PERMIT_TYPE_ALCOHOL_SERVING, null).getIssued()).isNull();
	}

	@Test
	void toAssetClosureRequestEndsThePermitOnALastDayThatHasPassed() {
		final var decision = new Decision()
			.validTo(LocalDate.of(2026, 9, 30))
			.decidedAt(OffsetDateTime.of(2026, 9, 19, 10, 0, 0, 0, ZoneOffset.UTC));

		final var result = toAssetClosureRequest(new Asset(), decision, TODAY);

		assertThat(result.getStatus()).isEqualTo(EXPIRED);
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(result).hasAllNullFieldsOrPropertiesExcept("status", "validTo");
	}

	@Test
	void toAssetClosureRequestLeavesALastDayStillToComeToPartyAssets() {
		final var lastDay = TODAY.plusDays(1);

		final var result = toAssetClosureRequest(new Asset(), new Decision().validTo(lastDay), TODAY);

		assertThat(result.getValidTo()).isEqualTo(lastDay);
		assertThat(result).hasAllNullFieldsOrPropertiesExcept("validTo");
	}

	@Test
	void toAssetClosureRequestKeepsThePermitValidOnItsLastDay() {
		final var result = toAssetClosureRequest(new Asset(), new Decision().validTo(TODAY), TODAY);

		assertThat(result.getValidTo()).isEqualTo(TODAY);
		assertThat(result).hasAllNullFieldsOrPropertiesExcept("validTo");
	}

	@Test
	void toAssetClosureRequestNeverEndsThePermitLaterThanItAlreadyEnds() {
		final var current = new Asset().validTo(TODAY.plusDays(30));

		final var result = toAssetClosureRequest(current, new Decision().validTo(TODAY.plusDays(90)), TODAY);

		assertThat(result.getValidTo()).isEqualTo(TODAY.plusDays(30));
		assertThat(result).hasAllNullFieldsOrPropertiesExcept("validTo");
	}

	@Test
	void toAssetClosureRequestEndsThePermitOnTheLastDayOfTheDecisionBeforeTheLastDayOfThePermit() {
		final var current = new Asset().validTo(TODAY.plusDays(90));

		final var result = toAssetClosureRequest(current, new Decision().validTo(TODAY.plusDays(30)), TODAY);

		assertThat(result.getValidTo()).isEqualTo(TODAY.plusDays(30));
	}

	@Test
	void toAssetClosureRequestEndsThePermitOnTheSwedishDayOfTheDecisionWithoutALastDay() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 19, 22, 30, 0, 0, ZoneOffset.UTC));

		final var result = toAssetClosureRequest(new Asset(), decision, TODAY);

		assertThat(result.getStatus()).isEqualTo(EXPIRED);
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void toAssetClosureRequestEndsThePermitTodayWithoutAnyDate() {
		final var result = toAssetClosureRequest(new Asset(), new Decision(), TODAY);

		assertThat(result.getValidTo()).isEqualTo(TODAY);
		assertThat(result).hasAllNullFieldsOrPropertiesExcept("validTo");
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

	@Test
	void toAssetUpdateRequestKeepsTheRestaurantNumberOfThePermit() {
		final var current = new Asset().additionalParameters(Map.of(PERMIT_PARAMETER_ERRAND_ID, "granting-errand-id", PERMIT_PARAMETER_RESTAURANT_NUMBER, "22810001"));
		final var decision = new Decision().parameters(List.of(new Parameter().key(PERMIT_PARAMETER_RESTAURANT_NUMBER).values(List.of())));

		assertThat(toAssetUpdateRequest(current, decision).getAdditionalParameters()).containsEntry(PERMIT_PARAMETER_RESTAURANT_NUMBER, "22810001");
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

	/** An addition is a serving permit of its own, which alcohol-serving-change can change like the main one. */
	@Test
	void toPermitTypeGivesTheServingProcessesOneType() {
		assertThat(toPermitType(PROCESS_KEY_ALCOHOL_SERVING)).isEqualTo(PERMIT_TYPE_ALCOHOL_SERVING);
		assertThat(toPermitType(PROCESS_KEY_ALCOHOL_SERVING_CHANGE)).isEqualTo(PERMIT_TYPE_ALCOHOL_SERVING);
		assertThat(toPermitType(PROCESS_KEY_ALCOHOL_SERVING_ADDITION)).isEqualTo(PERMIT_TYPE_ALCOHOL_SERVING);
	}

	@Test
	void toPermitTypeGivesEachLowAlcoholBeerProcessATypeOfItsOwn() {
		assertThat(toPermitType(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)).isEqualTo(PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES);
		assertThat(toPermitType(PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING)).isEqualTo(PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING);
		assertThat(toPermitType(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING)).isEqualTo(PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING);
	}

	@Test
	void toPermitTypeGivesTheTobaccoProcessesOneTypeAndECigarettesAnother() {
		assertThat(toPermitType(PROCESS_KEY_TOBACCO_SALES)).isEqualTo(PERMIT_TYPE_TOBACCO_SALES);
		assertThat(toPermitType(PROCESS_KEY_TOBACCO_SALES_CHANGE)).isEqualTo(PERMIT_TYPE_TOBACCO_SALES);
		assertThat(toPermitType(PROCESS_KEY_TOBACCO_SALES_CLOSURE)).isEqualTo(PERMIT_TYPE_TOBACCO_SALES);
		assertThat(toPermitType(PROCESS_KEY_E_CIGARETTE_SALES)).isEqualTo(PERMIT_TYPE_E_CIGARETTE_SALES);
	}

	@Test
	void toPermitTypeRefusesAProcessWithoutAPermit() {
		assertThatThrownBy(() -> toPermitType(PROCESS_KEY_CATERING_OCCASION))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(PROCESS_KEY_CATERING_OCCASION)
			.hasMessageContaining(PROCESS_KEY_ALCOHOL_SERVING);
		assertThatThrownBy(() -> toPermitType(null))
			.isInstanceOf(NonRetryableException.class);
	}
}
