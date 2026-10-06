package se.sundsvall.alkt.integration.licensedbusiness.mapper;

import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.LicenseHolder;
import generated.se.sundsvall.supportmanagement.Decision;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.alkt.exception.NonRetryableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicensedBusinessMapperTest {

	private static final String ERRAND_ID = "errand-id";
	private static final Map<String, String> PARAMETERS = Map.of(
		"premisesName", "Harrys Pub",
		"premisesStreetAddress", "Storgatan 33",
		"premisesPostalCode", "852 30",
		"premisesPostalArea", "Sundsvall");

	@Test
	void toAddressTakesThePremisesAddressOfTheErrand() {
		final var address = LicensedBusinessMapper.toAddress(PARAMETERS, ERRAND_ID);

		assertThat(address.getStreetAddress()).isEqualTo("Storgatan 33");
		assertThat(address.getPostalCode()).isEqualTo("852 30");
		assertThat(address.getPostalArea()).isEqualTo("Sundsvall");
		assertThat(address.getId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"premisesStreetAddress", "premisesPostalCode", "premisesPostalArea"
	})
	void toAddressFailsWithoutAPartOfTheAddress(final String missing) {
		final var parameters = new HashMap<>(PARAMETERS);
		parameters.remove(missing);

		assertThatThrownBy(() -> LicensedBusinessMapper.toAddress(parameters, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no premises address, parameters [%s] are missing".formatted(missing));
	}

	@Test
	void toAssignmentCreateRequestTakesTheHolderThePremisesAndThePeriodOfTheDecision() {
		final var decision = new Decision().validFrom(LocalDate.of(2026, 3, 1)).validTo(LocalDate.of(2026, 12, 31));

		final var assignment = LicensedBusinessMapper.toAssignmentCreateRequest("number-id", "address-id", "5566124144", "Krogen AB", PARAMETERS, decision);

		assertThat(assignment.getRestaurantNumberId()).isEqualTo("number-id");
		assertThat(assignment.getAddressId()).isEqualTo("address-id");
		assertThat(assignment.getOrgNumber()).isEqualTo("5566124144");
		assertThat(assignment.getHolderName()).isEqualTo("Krogen AB");
		assertThat(assignment.getPremisesName()).isEqualTo("Harrys Pub");
		assertThat(assignment.getValidFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(assignment.getValidTo()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void toAssignmentCreateRequestLeavesOutAPremisesNameTheErrandDoesNotHave() {
		final var assignment = LicensedBusinessMapper.toAssignmentCreateRequest("number-id", "address-id", "5566124144", "Krogen AB", Map.of(),
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

	@Test
	void isAssignedToDoesNotMatchAnotherHolderAnotherDayOrNoHolder() {
		final var assignment = new Assignment().licenseHolder(new LicenseHolder().orgNumber("5566124144")).validFrom(LocalDate.of(2026, 3, 1));

		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, "5590001111", LocalDate.of(2026, 3, 1))).isFalse();
		assertThat(LicensedBusinessMapper.isAssignedTo(assignment, "5566124144", LocalDate.of(2026, 3, 2))).isFalse();
		assertThat(LicensedBusinessMapper.isAssignedTo(new Assignment().validFrom(LocalDate.of(2026, 3, 1)), "5566124144", LocalDate.of(2026, 3, 1))).isFalse();
	}
}
