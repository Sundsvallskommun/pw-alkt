package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

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

	/**
	 * The restaurant number the license holder now has at this address. A free number on the address is reused, and a
	 * new one is allocated when there is none. The assignment is sent as given, except addressId and restaurantNumberId,
	 * which are set from the address and the number this call resolved.
	 */
	public String assignRestaurantNumber(final String municipalityId, final Address address, final AssignmentCreateRequest assignment) {
		final var addressId = findOrCreateAddress(municipalityId, address);
		final var restaurantNumber = availableOrNewRestaurantNumber(municipalityId, addressId);

		licensedBusinessClient.createAssignment(municipalityId, assignment
			.restaurantNumberId(restaurantNumber.getId())
			.addressId(addressId));

		return restaurantNumber.getNumber();
	}

	private String findOrCreateAddress(final String municipalityId, final Address address) {
		return lookupAddress(municipalityId, address)
			.orElseGet(() -> createAddress(municipalityId, address));
	}

	// A 409 means someone created the same address between the lookup and the create, so its id is there to be read now.
	private String createAddress(final String municipalityId, final Address address) {
		try {
			return getIdOfCreatedResource(licensedBusinessClient.createAddress(municipalityId, address), SERVICE);
		} catch (final ClientProblem e) {
			if (!CONFLICT.equals(e.getStatus())) {
				throw e;
			}
			return lookupAddress(municipalityId, address)
				.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Licensed business refused the address as a duplicate but does not know it"));
		}
	}

	private Optional<String> lookupAddress(final String municipalityId, final Address address) {
		return licensedBusinessClient.lookupAddress(municipalityId, address.getStreetAddress(), address.getPostalCode())
			.map(Address::getId);
	}

	private RestaurantNumber availableOrNewRestaurantNumber(final String municipalityId, final String addressId) {
		return licensedBusinessClient.getAvailableRestaurantNumbers(municipalityId, addressId).stream()
			.findFirst()
			.orElseGet(() -> createRestaurantNumber(municipalityId, addressId));
	}

	private RestaurantNumber createRestaurantNumber(final String municipalityId, final String addressId) {
		final var number = getIdOfCreatedResource(licensedBusinessClient.createRestaurantNumber(municipalityId, addressId), SERVICE);

		return licensedBusinessClient.getRestaurantNumber(municipalityId, number)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Restaurant number '%s' was created but cannot be read back".formatted(number)));
	}
}
