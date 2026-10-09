package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AddressRestaurantNumber;
import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.licensedbusiness.AddressRestaurantNumber.StatusEnum.ACTIVE;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class LicensedBusinessIntegration {

	private static final String SERVICE = "Licensed business";

	private final LicensedBusinessClient licensedBusinessClient;

	LicensedBusinessIntegration(final LicensedBusinessClient licensedBusinessClient) {
		this.licensedBusinessClient = licensedBusinessClient;
	}

	public String findOrCreateAddress(final String municipalityId, final Address address) {
		return findAddressId(municipalityId, address)
			.orElseGet(() -> createAddress(municipalityId, address));
	}

	public Optional<String> findAddressId(final String municipalityId, final Address address) {
		return licensedBusinessClient.lookupAddress(municipalityId, address.getStreetAddress(), address.getPostalCode())
			.map(Address::getId);
	}

	/** Any number at the address counts, whether it is free or held by an active assignment. */
	public boolean isRestaurantNumberAtAddress(final String municipalityId, final String addressId, final String number) {
		return licensedBusinessClient.getAddressRestaurantNumbers(municipalityId, addressId).stream()
			.anyMatch(restaurantNumber -> number.equals(restaurantNumber.getNumber()));
	}

	public List<String> getActiveRestaurantNumbers(final String municipalityId, final String addressId) {
		return licensedBusinessClient.getAddressRestaurantNumbers(municipalityId, addressId).stream()
			.filter(restaurantNumber -> ACTIVE.equals(restaurantNumber.getStatus()))
			.map(AddressRestaurantNumber::getNumber)
			.toList();
	}

	public List<String> getAvailableRestaurantNumbers(final String municipalityId, final String addressId) {
		return licensedBusinessClient.getAvailableRestaurantNumbers(municipalityId, addressId).stream()
			.map(RestaurantNumber::getNumber)
			.toList();
	}

	public String createRestaurantNumber(final String municipalityId, final String addressId) {
		final var response = requireSuccess(licensedBusinessClient.createRestaurantNumber(municipalityId, addressId));
		return getIdOfCreatedResource(response, SERVICE);
	}

	public String getRestaurantNumberId(final String municipalityId, final String number) {
		return licensedBusinessClient.getRestaurantNumber(municipalityId, number)
			.map(RestaurantNumber::getId)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Restaurant number '%s' cannot be read from licensed business".formatted(number)));
	}

	public Optional<Assignment> findLatestAssignment(final String municipalityId, final String number) {
		return licensedBusinessClient.getLatestAssignment(municipalityId, number);
	}

	public void createAssignment(final String municipalityId, final AssignmentCreateRequest assignment) {
		requireSuccess(licensedBusinessClient.createAssignment(municipalityId, assignment));
	}

	// A 409 means someone created the same address between the lookup and the create, so its id is there to be read now.
	private String createAddress(final String municipalityId, final Address address) {
		try {
			final var response = requireSuccess(licensedBusinessClient.createAddress(municipalityId, address));
			return getIdOfCreatedResource(response, SERVICE);
		} catch (final ClientProblem e) {
			if (!CONFLICT.equals(e.getStatus())) {
				throw e;
			}
			return findAddressId(municipalityId, address)
				.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Licensed business refused the address as a duplicate but does not know it"));
		}
	}

	// dismiss404 also turns a 404 on a create into a response, and only the Optional reads want that.
	private static ResponseEntity<Void> requireSuccess(final ResponseEntity<Void> response) {
		return Optional.of(response)
			.filter(created -> created.getStatusCode().is2xxSuccessful())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "%s answered %s to a create".formatted(SERVICE, response.getStatusCode().value())));
	}
}
