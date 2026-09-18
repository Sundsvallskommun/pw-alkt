package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

@Component
public class LicensedBusinessIntegration {

	private final LicensedBusinessClient licensedBusinessClient;

	LicensedBusinessIntegration(final LicensedBusinessClient licensedBusinessClient) {
		this.licensedBusinessClient = licensedBusinessClient;
	}

	/**
	 * The restaurant number the licence holder now has at this address. A free number on the address is reused, and a
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
			return locationSegmentOf(licensedBusinessClient.createAddress(municipalityId, address));
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
		final var number = locationSegmentOf(licensedBusinessClient.createRestaurantNumber(municipalityId, addressId));

		return licensedBusinessClient.getRestaurantNumber(municipalityId, number)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Restaurant number '%s' was created but cannot be read back".formatted(number)));
	}

	// Location points at the path the created resource is read from, so its last segment identifies it.
	private static String locationSegmentOf(final ResponseEntity<Void> response) {
		return Optional.ofNullable(response)
			.map(ResponseEntity::getHeaders)
			.map(HttpHeaders::getLocation)
			.map(URI::getPath)
			.map(path -> substringAfterLast(path, "/"))
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Licensed business created a resource without saying which"));
	}
}
