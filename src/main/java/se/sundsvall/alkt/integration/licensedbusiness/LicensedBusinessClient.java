package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.alkt.integration.licensedbusiness.configuration.LicensedBusinessConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.licensedbusiness.configuration.LicensedBusinessConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	url = "${integration.licensed-business.url}",
	configuration = LicensedBusinessConfiguration.class,
	dismiss404 = true)
@CircuitBreaker(name = CLIENT_ID)
public interface LicensedBusinessClient {

	@GetMapping(path = "/{municipalityId}/addresses/lookup", produces = APPLICATION_JSON_VALUE)
	Optional<Address> lookupAddress(
		@PathVariable String municipalityId,
		@RequestParam String streetAddress,
		@RequestParam String postalCode);

	@PostMapping(path = "/{municipalityId}/addresses", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAddress(
		@PathVariable String municipalityId,
		@RequestBody Address address);

	@GetMapping(path = "/{municipalityId}/restaurant-numbers/available", produces = APPLICATION_JSON_VALUE)
	List<RestaurantNumber> getAvailableRestaurantNumbers(
		@PathVariable String municipalityId,
		@RequestParam String addressId);

	@PostMapping(path = "/{municipalityId}/restaurant-numbers", produces = ALL_VALUE)
	ResponseEntity<Void> createRestaurantNumber(
		@PathVariable String municipalityId,
		@RequestParam String addressId);

	@GetMapping(path = "/{municipalityId}/restaurant-numbers/{restaurantNumber}", produces = APPLICATION_JSON_VALUE)
	Optional<RestaurantNumber> getRestaurantNumber(
		@PathVariable String municipalityId,
		@PathVariable String restaurantNumber);

	@PostMapping(path = "/{municipalityId}/assignments", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAssignment(
		@PathVariable String municipalityId,
		@RequestBody AssignmentCreateRequest assignment);
}
