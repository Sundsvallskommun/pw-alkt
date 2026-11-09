package se.sundsvall.alkt.integration.licensedbusiness.mapper;

import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.LicenseHolder;
import generated.se.sundsvall.supportmanagement.Decision;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.alkt.exception.NonRetryableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicensedBusinessMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String ERRAND_ID = "errand-id";
	private static final JsonNode DECISION_JSON = json("""
		{ "premises": { "name": " Harrys Pub ", "address": { "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" } } }
		""");

	@Test
	void toAddressTakesThePremisesAddressOfTheDecision() {
		final var address = LicensedBusinessMapper.toAddress(DECISION_JSON, ERRAND_ID);

		assertThat(address.getStreetAddress()).isEqualTo("Storgatan 33");
		assertThat(address.getPostalCode()).isEqualTo("852 30");
		assertThat(address.getPostalArea()).isEqualTo("Sundsvall");
		assertThat(address.getId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"street", "postalCode", "city"
	})
	void toAddressFailsWithoutAPartOfTheAddress(final String missing) {
		final var decisionJson = DECISION_JSON.deepCopy();
		((ObjectNode) decisionJson.path("premises").path("address")).put(missing, " ");

		assertThatThrownBy(() -> LicensedBusinessMapper.toAddress(decisionJson, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The decision of errand 'errand-id' does not name all of the premises address, it lacks premises.address.%s".formatted(missing));
	}

	@Test
	void toAddressFailsWithoutThePremises() {
		assertThatThrownBy(() -> LicensedBusinessMapper.toAddress(json("{}"), ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The decision of errand 'errand-id' does not name all of the premises address, it lacks premises.address.street, premises.address.postalCode, "
				+ "premises.address.city");
	}

	@Test
	void findChosenRestaurantNumberTakesTheNumberOfThePremisesOfTheDecision() {
		assertThat(LicensedBusinessMapper.findChosenRestaurantNumber(json("""
			{ "premises": { "restaurantNumber": " 22810001 " } }
			"""))).contains("22810001");
		assertThat(LicensedBusinessMapper.findChosenRestaurantNumber(json("""
			{ "premises": { "restaurantNumber": "" } }
			"""))).isEmpty();
		assertThat(LicensedBusinessMapper.findChosenRestaurantNumber(json("{}"))).isEmpty();
	}

	/**
	 * The process writes the number it created beside the request for a new one, and a rerun must not take it as a choice.
	 */
	@Test
	void findChosenRestaurantNumberFindsNoChoiceBesideARequestForANewNumber() {
		final var decisionJson = json("""
			{ "premises": { "newRestaurantNumber": true, "restaurantNumber": "22810002" } }
			""");

		assertThat(LicensedBusinessMapper.findChosenRestaurantNumber(decisionJson)).isEmpty();
		assertThat(LicensedBusinessMapper.findRestaurantNumber(decisionJson)).contains("22810002");
	}

	@Test
	void findChosenRestaurantNumberTakesTheNumberBesideANewNumberThatIsNotAsked() {
		assertThat(LicensedBusinessMapper.findChosenRestaurantNumber(json("""
			{ "premises": { "newRestaurantNumber": false, "restaurantNumber": "22810001" } }
			"""))).contains("22810001");
	}

	@Test
	void withRestaurantNumberPutsTheNumberAtThePremisesOfACopy() {
		final var decisionJson = json("""
			{ "premises": { "name": "Harrys Pub", "newRestaurantNumber": true }, "beverages": [ "WINE" ] }
			""");

		final var result = LicensedBusinessMapper.withRestaurantNumber(decisionJson, "22810002");

		assertThat(result).isEqualTo(json("""
			{ "premises": { "name": "Harrys Pub", "newRestaurantNumber": true, "restaurantNumber": "22810002" }, "beverages": [ "WINE" ] }
			"""));
		assertThat(decisionJson.path("premises").has("restaurantNumber")).isFalse();
	}

	@Test
	void withRestaurantNumberReplacesTheNumberAlreadyThere() {
		final var result = LicensedBusinessMapper.withRestaurantNumber(json("""
			{ "premises": { "newRestaurantNumber": true, "restaurantNumber": "22810001" } }
			"""), "22810002");

		assertThat(result.path("premises").path("restaurantNumber").asString()).isEqualTo("22810002");
	}

	@Test
	void asksForANewRestaurantNumberOnlyByTheBooleanTrue() {
		assertThat(LicensedBusinessMapper.asksForANewRestaurantNumber(json("""
			{ "premises": { "newRestaurantNumber": true } }
			"""))).isTrue();
		assertThat(LicensedBusinessMapper.asksForANewRestaurantNumber(json("""
			{ "premises": { "newRestaurantNumber": "true" } }
			"""))).isFalse();
		assertThat(LicensedBusinessMapper.asksForANewRestaurantNumber(json("""
			{ "premises": { "newRestaurantNumber": false } }
			"""))).isFalse();
		assertThat(LicensedBusinessMapper.asksForANewRestaurantNumber(json("{}"))).isFalse();
	}

	@Test
	void toAssignmentCreateRequestTakesTheHolderThePremisesAndThePeriodOfTheDecision() {
		final var decision = new Decision().validFrom(LocalDate.of(2026, 3, 1)).validTo(LocalDate.of(2026, 12, 31));

		final var assignment = LicensedBusinessMapper.toAssignmentCreateRequest("number-id", "address-id", "5566124144", "Krogen AB", DECISION_JSON, decision);

		assertThat(assignment.getRestaurantNumberId()).isEqualTo("number-id");
		assertThat(assignment.getAddressId()).isEqualTo("address-id");
		assertThat(assignment.getOrgNumber()).isEqualTo("5566124144");
		assertThat(assignment.getHolderName()).isEqualTo("Krogen AB");
		assertThat(assignment.getPremisesName()).isEqualTo("Harrys Pub");
		assertThat(assignment.getValidFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(assignment.getValidTo()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void toAssignmentCreateRequestLeavesOutAPremisesNameTheDecisionDoesNotHave() {
		final var assignment = LicensedBusinessMapper.toAssignmentCreateRequest("number-id", "address-id", "5566124144", "Krogen AB", json("{}"),
			new Decision().validFrom(LocalDate.of(2026, 3, 1)));

		assertThat(assignment.getPremisesName()).isNull();
		assertThat(assignment.getValidTo()).isNull();
	}

	/** Decided late on the last day of February in UTC, which is already March in Sweden. */
	@Test
	void toValidFromFallsBackOnTheSwedishDayOfTheDecision() {
		final var decision = new Decision().decidedAt(OffsetDateTime.of(2026, 2, 28, 23, 30, 0, 0, ZoneOffset.UTC));

		assertThat(LicensedBusinessMapper.toValidFrom(decision)).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	void toValidFromFailsWithoutADayToStartBy() {
		assertThatThrownBy(() -> LicensedBusinessMapper.toValidFrom(new Decision().id("decision-id")))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("Decision decision-id has neither validFrom nor decidedAt");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"5566124144", "556612-4144"
	})
	void isAssignedToMatchesTheHolderWithOrWithoutTheHyphen(final String orgNumber) {
		final var assignment = new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).validFrom(LocalDate.of(2026, 3, 1));

		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, orgNumber, LocalDate.of(2026, 3, 1))).isTrue();
	}

	@Test
	void isActiveForMatchesAnActiveAssignmentOfTheHolderOnly() {
		final var active = new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).status("ACTIVE");
		final var ended = new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).status("ENDED");

		assertThat(LicensedBusinessMapper.isActiveFor(active, "5566124144")).isTrue();
		assertThat(LicensedBusinessMapper.isActiveFor(active, "5590001111")).isFalse();
		assertThat(LicensedBusinessMapper.isActiveFor(ended, "5566124144")).isFalse();
	}

	/** Party gives a sole trader's personal identity number with its century, the import kept it without. */
	@Test
	void isActiveForMatchesAPersonalIdentityNumberWithOrWithoutItsCentury() {
		final var imported = new Assignment().licenseHolder(new LicenseHolder().orgNumber("800101-1234")).status("ACTIVE");
		final var created = new Assignment().licenseHolder(new LicenseHolder().orgNumber("198001011234")).status("ACTIVE");

		assertThat(LicensedBusinessMapper.isActiveFor(imported, "198001011234")).isTrue();
		assertThat(LicensedBusinessMapper.isActiveFor(created, "198001011234")).isTrue();
		assertThat(LicensedBusinessMapper.isActiveFor(imported, "198001011235")).isFalse();
	}

	@Test
	void isAssignedToMatchesAnOrganisationNumberWithThePrefix16() {
		final var assignment = new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).validFrom(LocalDate.of(2026, 3, 1));

		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, "165566124144", LocalDate.of(2026, 3, 1))).isTrue();
	}

	@Test
	void isAssignedToDoesNotMatchAnotherHolderAnotherDayOrNoHolder() {
		final var assignment = new Assignment().licenseHolder(new LicenseHolder().orgNumber("5566124144")).validFrom(LocalDate.of(2026, 3, 1));

		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, "5590001111", LocalDate.of(2026, 3, 1))).isFalse();
		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, "5566124144", LocalDate.of(2026, 3, 2))).isFalse();
		assertThat(LicensedBusinessMapper.isAssignedTo(new Assignment().validFrom(LocalDate.of(2026, 3, 1)), "5566124144", LocalDate.of(2026, 3, 1))).isFalse();
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
