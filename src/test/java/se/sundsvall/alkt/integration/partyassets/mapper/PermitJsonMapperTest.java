package se.sundsvall.alkt.integration.partyassets.mapper;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.alkt.exception.NonRetryableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.sundsvall.alkt.integration.partyassets.mapper.PermitJsonMapper.toChangedPermit;
import static se.sundsvall.alkt.integration.partyassets.mapper.PermitJsonMapper.toGrantedPermit;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

class PermitJsonMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String ERRAND_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

	@Test
	void toGrantedPermitTakesTheDecisionAndSetsWhatTheProcessOwns() {
		final var decision = json("""
			{
			  "errandId": "another-errand",
			  "caseRegistrationNumber": "ANOTHER-NUMBER",
			  "externalCaseRegistrationNumber": "2026/0456",
			  "servingPermitType": "PERMANENT_SERVING",
			  "premises": { "name": "Restaurang Ankaret", "restaurantNumber": "22819999", "newRestaurantNumber": true, "municipalityId": "2262" },
			  "beverages": ["WINE", "STRONG_BEER"],
			  "conditions": ["Från beslutets JSON."],
			  "decision": { "caseRegistrationNumber": "ANOTHER-NUMBER", "outcome": "APPROVAL_WITH_CONDITIONS", "decisionText": "Bifall med villkor" }
			}
			""");

		final var result = toGrantedPermit(ALCOHOL, decision, ERRAND_ID, "ALKT-2026-000123", "22620001", List.of("Ordningsvakt efter 23.00.", "Bordsservering ute."));

		assertThat(result).isEqualTo(json("""
			{
			  "errandId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
			  "caseRegistrationNumber": "ALKT-2026-000123",
			  "externalCaseRegistrationNumber": "2026/0456",
			  "servingPermitType": "PERMANENT_SERVING",
			  "premises": { "name": "Restaurang Ankaret", "restaurantNumber": "22620001", "municipalityId": "2262" },
			  "beverages": ["WINE", "STRONG_BEER"],
			  "conditions": ["Ordningsvakt efter 23.00.", "Bordsservering ute."],
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000123", "outcome": "APPROVAL_WITH_CONDITIONS", "decisionText": "Bifall med villkor" }
			}
			"""));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void toGrantedPermitLeavesOutARestaurantNumberTheProcessDidNotAssign(final String restaurantNumber) {
		final var decision = json("""
			{ "premises": { "name": "Restaurang Ankaret", "restaurantNumber": "22819999" } }
			""");

		final var result = toGrantedPermit(ALCOHOL, decision, ERRAND_ID, "ALKT-2026-000123", restaurantNumber, List.of());

		assertThat(result.path("premises")).isEqualTo(json("""
			{ "name": "Restaurang Ankaret" }
			"""));
	}

	@Test
	void toGrantedPermitGivesThePremisesTheRestaurantNumberWhenTheDecisionHasNoPremises() {
		final var result = toGrantedPermit(ALCOHOL, json("{}"), ERRAND_ID, "ALKT-2026-000123", "22810001", List.of());

		assertThat(result.path("premises")).isEqualTo(json("""
			{ "restaurantNumber": "22810001" }
			"""));
	}

	@Test
	void toGrantedPermitLeavesOutTheConditionsOfTheDecisionJsonWithoutTerms() {
		final var decision = json("""
			{ "conditions": ["Från beslutets JSON."] }
			""");

		final var result = toGrantedPermit(ALCOHOL, decision, ERRAND_ID, "ALKT-2026-000123", null, List.of());

		assertThat(result.has("conditions")).isFalse();
	}

	/** The schema has no null, so a field the decision gives as null is left out, as a change removes one. */
	@Test
	void toGrantedPermitLeavesOutTheFieldsTheDecisionGivesAsNull() {
		final var decision = json("""
			{ "servingArea": { "description": "Matsal", "seatsOutdoor": null }, "additionalScopes": null, "servingHours": [{ "from": "11:00", "to": "01:00" }] }
			""");

		final var result = toGrantedPermit(ALCOHOL, decision, ERRAND_ID, "ALKT-2026-000123", null, List.of());

		assertThat(result.path("servingArea")).isEqualTo(json("""
			{ "description": "Matsal" }
			"""));
		assertThat(result.has("additionalScopes")).isFalse();
		assertThat(result.path("servingHours")).isEqualTo(json("""
			[{ "from": "11:00", "to": "01:00" }]
			"""));
	}

	@Test
	void toGrantedPermitOfTobaccoHasNoRestaurantNumberAndGetsADecision() {
		final var decision = json("""
			{ "pointOfSale": { "name": "Tobaksaffären", "municipalityId": "2262" }, "salesTypes": ["RETAIL"] }
			""");

		final var result = toGrantedPermit(TOBACCO, decision, ERRAND_ID, "ALKT-2026-000200", "22810001", List.of("Försäljning bara från disken."));

		assertThat(result).isEqualTo(json("""
			{
			  "pointOfSale": { "name": "Tobaksaffären", "municipalityId": "2262" },
			  "salesTypes": ["RETAIL"],
			  "errandId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
			  "caseRegistrationNumber": "ALKT-2026-000200",
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000200" },
			  "conditions": ["Försäljning bara från disken."]
			}
			"""));
	}

	@Test
	void toGrantedPermitLeavesTheJsonOfTheDecisionAsItIs() {
		final var decision = json("""
			{ "premises": { "newRestaurantNumber": true, "restaurantNumber": "22819999" }, "decision": { "outcome": "APPROVAL" } }
			""");
		final var before = decision.deepCopy();

		toGrantedPermit(ALCOHOL, decision, ERRAND_ID, "ALKT-2026-000123", "22810001", List.of("Villkor."));

		assertThat(decision).isEqualTo(before);
	}

	@Test
	void toGrantedPermitFailsWithoutRetryOnADecisionThatIsNotAJsonObject() {
		assertThatThrownBy(() -> toGrantedPermit(ALCOHOL, json("[]"), ERRAND_ID, "ALKT-2026-000123", null, List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("A JSON parameter of a permit must be a JSON object, but was ARRAY");
		assertThatThrownBy(() -> toGrantedPermit(ALCOHOL, NullNode.getInstance(), ERRAND_ID, "ALKT-2026-000123", null, List.of()))
			.isInstanceOf(NonRetryableException.class);
		assertThatThrownBy(() -> toGrantedPermit(ALCOHOL, null, ERRAND_ID, "ALKT-2026-000123", null, List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("A JSON parameter of a permit must be a JSON object, but was missing");
	}

	@Test
	void toGrantedPermitFailsWithoutRetryWhenThePremisesIsNotAJsonObject() {
		assertThatThrownBy(() -> toGrantedPermit(ALCOHOL, json("""
			{ "premises": "Restaurang Ankaret" }
			"""), ERRAND_ID, "ALKT-2026-000123", "22810001", List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The field 'premises' of the JSON parameter of a permit must be a JSON object, but was STRING");
	}

	@Test
	void toChangedPermitPutsTheChangeOnTopOfThePermit() {
		final var permit = json("""
			{
			  "premises": { "name": "Restaurang Ankaret", "address": { "street": "Storgatan 1", "postalCode": "85230", "city": "Sundsvall" }, "phoneNumber": "060-123456" },
			  "beverages": ["WINE"],
			  "servingHours": [{ "from": "11:00", "to": "01:00" }, { "location": "OUTDOOR", "from": "11:00", "to": "22:00" }],
			  "servingArea": { "description": "Matsal", "seatsIndoor": 80, "seatsOutdoor": 40 }
			}
			""");
		final var change = json("""
			{
			  "premises": { "name": "Ankaret Bar & Kök", "address": { "street": "Storgatan 3" } },
			  "servingHours": [{ "from": "11:00", "to": "02:00" }],
			  "servingArea": { "seatsOutdoor": null }
			}
			""");

		final var result = toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of());

		assertThat(result).isEqualTo(json("""
			{
			  "premises": { "name": "Ankaret Bar & Kök", "address": { "street": "Storgatan 3", "postalCode": "85230", "city": "Sundsvall" }, "phoneNumber": "060-123456" },
			  "beverages": ["WINE"],
			  "servingHours": [{ "from": "11:00", "to": "02:00" }],
			  "servingArea": { "description": "Matsal", "seatsIndoor": 80 },
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000300" }
			}
			"""));
	}

	@Test
	void toChangedPermitNeverChangesTheErrandTheCaseNumberTheMigrationOrTheRestaurantNumber() {
		final var permit = json("""
			{
			  "errandId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
			  "caseRegistrationNumber": "IAN-2019-45",
			  "migration": { "sourceSystem": "ALKT", "issuedDateEstimated": false },
			  "premises": { "name": "Restaurang Ankaret", "restaurantNumber": "22810012" }
			}
			""");
		final var change = json("""
			{
			  "errandId": "change-errand",
			  "caseRegistrationNumber": null,
			  "migration": { "sourceSystem": "ALKOL", "issuedDateEstimated": true },
			  "premises": { "name": "Ankaret", "restaurantNumber": "22819999" }
			}
			""");

		final var result = toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of());

		assertThat(result.path("errandId").asString()).isEqualTo("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
		assertThat(result.path("caseRegistrationNumber").asString()).isEqualTo("IAN-2019-45");
		assertThat(result.path("migration")).isEqualTo(json("""
			{ "sourceSystem": "ALKT", "issuedDateEstimated": false }
			"""));
		assertThat(result.path("premises")).isEqualTo(json("""
			{ "name": "Ankaret", "restaurantNumber": "22810012" }
			"""));
	}

	@Test
	void toChangedPermitTakesTheExternalCaseRegistrationNumberOfTheChangeAndKeepsItWhenTheChangeHasNone() {
		final var permit = json("""
			{ "caseRegistrationNumber": "IAN-2019-45", "externalCaseRegistrationNumber": "2026/0456" }
			""");

		final var changed = toChangedPermit(ALCOHOL, permit, json("""
			{ "externalCaseRegistrationNumber": "2026/0789" }
			"""), "ALKT-2026-000300", List.of());
		final var unchanged = toChangedPermit(ALCOHOL, permit, json("{}"), "ALKT-2026-000300", List.of());

		assertThat(changed.path("externalCaseRegistrationNumber").asString()).isEqualTo("2026/0789");
		assertThat(changed.path("caseRegistrationNumber").asString()).isEqualTo("IAN-2019-45");
		assertThat(unchanged.path("externalCaseRegistrationNumber").asString()).isEqualTo("2026/0456");
	}

	@Test
	void toChangedPermitKeepsTheRestaurantNumberWhenTheChangeRemovesThePremises() {
		final var permit = json("""
			{ "premises": { "name": "Restaurang Ankaret", "restaurantNumber": "22810012" } }
			""");

		final var result = toChangedPermit(ALCOHOL, permit, json("""
			{ "premises": null }
			"""), "ALKT-2026-000300", List.of());

		assertThat(result.path("premises")).isEqualTo(json("""
			{ "restaurantNumber": "22810012" }
			"""));
	}

	@Test
	void toChangedPermitGivesAPermitWithoutRestaurantNumberNoneFromTheChange() {
		final var permit = json("""
			{ "premises": { "name": "Restaurang Ankaret" } }
			""");
		final var change = json("""
			{ "premises": { "restaurantNumber": "22819999", "newRestaurantNumber": true } }
			""");

		final var result = toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of());

		assertThat(result.path("premises")).isEqualTo(json("""
			{ "name": "Restaurang Ankaret" }
			"""));
	}

	@Test
	void toChangedPermitReplacesTheDecisionWithTheOneOfTheChange() {
		final var permit = json("""
			{ "decision": { "caseRegistrationNumber": "ALKT-2026-000123", "outcome": "APPROVAL_WITH_CONDITIONS", "replacesDecision": "IAN-2019-45", "legalBasis": "8 kap. 2 § alkohollagen" } }
			""");
		final var change = json("""
			{ "decision": { "caseRegistrationNumber": "FROM-THE-CHANGE", "outcome": "APPROVAL", "decisionText": "Bifall", "decisionDate": "2026-11-05" } }
			""");

		final var result = toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of());

		assertThat(result.path("decision")).isEqualTo(json("""
			{ "caseRegistrationNumber": "ALKT-2026-000300", "outcome": "APPROVAL", "decisionText": "Bifall", "decisionDate": "2026-11-05" }
			"""));
	}

	@Test
	void toChangedPermitWithoutADecisionInTheChangeHasOnlyTheCaseNumberOfTheChange() {
		final var permit = json("""
			{ "decision": { "caseRegistrationNumber": "ALKT-2026-000123", "outcome": "APPROVAL" } }
			""");

		assertThat(toChangedPermit(ALCOHOL, permit, json("{}"), "ALKT-2026-000300", List.of()).path("decision")).isEqualTo(json("""
			{ "caseRegistrationNumber": "ALKT-2026-000300" }
			"""));
		assertThat(toChangedPermit(ALCOHOL, permit, json("""
			{ "decision": null }
			"""), "ALKT-2026-000300", List.of()).path("decision")).isEqualTo(json("""
			{ "caseRegistrationNumber": "ALKT-2026-000300" }
			"""));
	}

	/** The schema has no null, so null in the decision of the change or in an object of a list is left out as well. */
	@Test
	void toChangedPermitLeavesOutTheNullsOfTheDecisionAndOfTheObjectsOfAList() {
		final var change = json("""
			{ "decision": { "decisionText": "Bifall", "replacesDecision": null }, "servingHours": [{ "from": "11:00", "to": "02:00", "description": null }, null] }
			""");

		final var result = toChangedPermit(ALCOHOL, json("{}"), change, "ALKT-2026-000300", List.of());

		assertThat(result.path("decision")).isEqualTo(json("""
			{ "decisionText": "Bifall", "caseRegistrationNumber": "ALKT-2026-000300" }
			"""));
		assertThat(result.path("servingHours")).isEqualTo(json("""
			[{ "from": "11:00", "to": "02:00" }]
			"""));
	}

	@Test
	void toChangedPermitTakesTheConditionsFromTheTermsOfTheChange() {
		final var permit = json("""
			{ "conditions": ["Ordningsvakt efter 23.00."] }
			""");
		final var change = json("""
			{ "conditions": ["Från ändringens JSON."] }
			""");

		assertThat(toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of("Ordningsvakt efter 01.00.")).path("conditions")).isEqualTo(json("""
			["Ordningsvakt efter 01.00."]
			"""));
		assertThat(toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of()).path("conditions")).isEqualTo(json("""
			["Ordningsvakt efter 23.00."]
			"""));
	}

	@Test
	void toChangedPermitLeavesBothJsonParametersAsTheyAre() {
		final var permit = json("""
			{ "premises": { "name": "Restaurang Ankaret" }, "migration": { "sourceSystem": "ALKT" } }
			""");
		final var change = json("""
			{ "premises": { "name": "Ankaret", "newRestaurantNumber": true }, "migration": { "sourceSystem": "ALKOL" }, "decision": { "outcome": "APPROVAL" } }
			""");
		final var permitBefore = permit.deepCopy();
		final var changeBefore = change.deepCopy();

		toChangedPermit(ALCOHOL, permit, change, "ALKT-2026-000300", List.of("Villkor."));

		assertThat(permit).isEqualTo(permitBefore);
		assertThat(change).isEqualTo(changeBefore);
	}

	@Test
	void toChangedPermitFailsWithoutRetryWhenEitherIsNotAJsonObject() {
		assertThatThrownBy(() -> toChangedPermit(ALCOHOL, json("\"text\""), json("{}"), "ALKT-2026-000300", List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("A JSON parameter of a permit must be a JSON object, but was STRING");
		assertThatThrownBy(() -> toChangedPermit(ALCOHOL, json("{}"), json("[]"), "ALKT-2026-000300", List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("A JSON parameter of a permit must be a JSON object, but was ARRAY");
		assertThatThrownBy(() -> toChangedPermit(ALCOHOL, json("{}"), json("""
			{ "decision": "Bifall" }
			"""), "ALKT-2026-000300", List.of()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The field 'decision' of the JSON parameter of a permit must be a JSON object, but was STRING");
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
