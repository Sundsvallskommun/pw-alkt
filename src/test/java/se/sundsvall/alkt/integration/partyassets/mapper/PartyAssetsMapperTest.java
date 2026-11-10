package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.supportmanagement.Decision;
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
import tools.jackson.databind.node.JsonNodeFactory;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ATTACHMENT_PART_NAME;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.ORIGIN;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.findPermitParameter;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.isCertificate;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetClosureRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitParameter;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitUpdateRequest;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

class PartyAssetsMapperTest {

	private static final String ERRAND_ID = randomUUID().toString();
	private static final String PARTY_ID = randomUUID().toString();
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

	@Test
	void toAssetCreateRequestIssuesOnTheDayOfTheDecisionWithoutValidFrom() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 9, 20, 23, 30, 0, 0, ZoneOffset.ofHours(2)));

		final var result = toAssetCreateRequest(decision, PARTY_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, toPermitParameter(ALCOHOL, "2281_aot_alcohol_party_asset_0.1", JsonNodeFactory.instance.objectNode()));

		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void toAssetCreateRequestLeavesIssuedOutWithoutAnyDate() {
		assertThat(toAssetCreateRequest(new Decision(), PARTY_ID, LOW_ALCOHOL_BEER_SALES_NOTIFICATION, toPermitParameter(ALCOHOL, "2281_aot_alcohol_party_asset_0.1", JsonNodeFactory.instance.objectNode())).getIssued()).isNull();
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

	@Test
	void isCertificateKnowsTheCertificateByItsCategoryAlone() {
		assertThat(isCertificate(new AssetAttachment().category(toCertificateFile(new byte[0]).category()))).isTrue();
		assertThat(isCertificate(new AssetAttachment().category("Tillståndsbevis").fileName("annat.pdf"))).isTrue();
		assertThat(isCertificate(new AssetAttachment().category("LOKALRITNING").fileName("tillstandsbevis.pdf"))).isFalse();
		assertThat(isCertificate(new AssetAttachment().fileName("tillstandsbevis.pdf"))).isFalse();
	}

	@Test
	void toAssetCreateRequestCarriesTheDecisionAndOnlyTheJsonParameter() {
		final var decision = new Decision()
			.id("decision-id")
			.title("Beslut om serveringstillstånd")
			.validFrom(LocalDate.of(2026, 10, 1))
			.validTo(LocalDate.of(2027, 9, 30))
			.legalBasis("8 kap. 2 § alkohollagen")
			.parameters(List.of(new Parameter().key("serveringstid").values(List.of("11.00–01.00"))));
		final var permit = toPermitParameter(ALCOHOL, "2281_aot_alcohol_party_asset_0.1", JsonNodeFactory.instance.objectNode().put("errandId", ERRAND_ID));

		final var result = toAssetCreateRequest(decision, PARTY_ID, ALCOHOL_SERVING_PERMIT, permit);

		assertThat(result.getAssetId()).isEqualTo("decision-id");
		assertThat(result.getStatus()).isEqualTo(DRAFT);
		assertThat(result.getOrigin()).isEqualTo(ORIGIN);
		assertThat(result.getPartyId()).isEqualTo(PARTY_ID);
		assertThat(result.getType()).isEqualTo("AlcoholServingPermit");
		assertThat(result.getIssued()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(result.getTitle()).isEqualTo("Beslut om serveringstillstånd");
		assertThat(result.getJsonParameters()).containsExactly(permit);
		assertThat(result.getAdditionalParameters()).isNull();
		assertThat(result.getDescription()).isNull();
	}

	@Test
	void toPermitParameterIsKeyedByTheNameOfTheSchema() {
		final var value = JsonNodeFactory.instance.objectNode().put("errandId", ERRAND_ID);

		final var result = toPermitParameter(TOBACCO, "2281_aot_tobacco_party_asset_1.0", value);

		assertThat(result.getKey()).isEqualTo("aot_tobacco_party_asset");
		assertThat(result.getSchemaId()).isEqualTo("2281_aot_tobacco_party_asset_1.0");
		assertThat(result.getValue()).isSameAs(value);
	}

	/** party-assets replaces the whole list, so a JSON parameter of another key must be sent along unchanged. */
	@Test
	void toPermitUpdateRequestReplacesTheJsonParameterOfThePermitAndKeepsTheOthers() {
		final var other = new AssetJsonParameter().key("other").schemaId("2281_other_1.0").value(JsonNodeFactory.instance.objectNode().put("kept", true));
		final var old = new AssetJsonParameter().key("aot_alcohol_party_asset").schemaId("2281_aot_alcohol_party_asset_0.1").value(JsonNodeFactory.instance.objectNode());
		final var permit = new AssetJsonParameter().key("aot_alcohol_party_asset").schemaId("2281_aot_alcohol_party_asset_1.0").value(JsonNodeFactory.instance.objectNode().put("errandId", ERRAND_ID));
		final var current = new Asset().jsonParameters(List.of(old, other));

		final var result = toPermitUpdateRequest(current, new Decision().validTo(LocalDate.of(2027, 9, 30)), permit);

		assertThat(result.getJsonParameters()).containsExactly(other, permit);
		assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(result.getAdditionalParameters()).isNull();
		assertThat(result.getTitle()).isNull();
		assertThat(result.getStatus()).isNull();
	}

	/** An empty map clears the additional parameters in party-assets, while a missing one leaves them. */
	@Test
	void toPermitUpdateRequestRemovesTheAdditionalParametersOfThePermit() {
		final var permit = toPermitParameter(ALCOHOL, "2281_aot_alcohol_party_asset_0.1", JsonNodeFactory.instance.objectNode());
		final var current = new Asset().additionalParameters(Map.of("errandId", ERRAND_ID));

		final var result = toPermitUpdateRequest(current, new Decision(), permit);

		assertThat(result.getAdditionalParameters()).isNotNull().isEmpty();
		assertThat(result.getValidTo()).isNull();
		assertThat(result.getJsonParameters()).containsExactly(permit);
	}

	@Test
	void findPermitParameterFindsTheParameterOfTheSchema() {
		final var permit = new AssetJsonParameter().key("aot_alcohol_party_asset").schemaId("2281_aot_alcohol_party_asset_0.1").value(JsonNodeFactory.instance.objectNode());
		final var asset = new Asset().jsonParameters(List.of(new AssetJsonParameter().key("aot_tobacco_party_asset"), permit));

		assertThat(findPermitParameter(asset, ALCOHOL)).containsSame(permit);
		assertThat(findPermitParameter(new Asset().jsonParameters(List.of(permit)), TOBACCO)).isEmpty();
		assertThat(findPermitParameter(new Asset(), ALCOHOL)).isEmpty();
	}
}
