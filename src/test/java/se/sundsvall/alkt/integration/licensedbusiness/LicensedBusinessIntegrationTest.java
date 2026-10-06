package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AddressRestaurantNumber;
import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;

@ExtendWith(MockitoExtension.class)
class LicensedBusinessIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String STREET_ADDRESS = "Storgatan 1";
	private static final String POSTAL_CODE = "852 30";

	@Mock
	private LicensedBusinessClient licensedBusinessClientMock;

	@InjectMocks
	private LicensedBusinessIntegration licensedBusinessIntegration;

	@Test
	void findsAnAddressThatIsAlreadyKnown() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.of(address(addressId)));

		assertThat(licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null))).isEqualTo(addressId);

		verify(licensedBusinessClientMock, never()).createAddress(any(), any());
	}

	@Test
	void createsTheAddressWhenTheLookupFindsNone() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenReturn(created("/2281/addresses/" + addressId));

		assertThat(licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null))).isEqualTo(addressId);
	}

	/** Two errands on the same address race: the one that loses reads the address the other just created. */
	@Test
	void readsTheAddressAgainWhenTheCreateIsRefusedAsADuplicate() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE))
			.thenReturn(Optional.empty(), Optional.of(address(addressId)));
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(CONFLICT, "Address already exists"));

		assertThat(licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null))).isEqualTo(addressId);
	}

	/** The duplicate check and the lookup are meant to share a key, so this pair of answers means they do not. */
	@Test
	void failsWhenTheDuplicateIsRefusedButStillCannotBeFound() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(CONFLICT, "Address already exists"));

		assertThatThrownBy(() -> licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("does not know it");
	}

	@Test
	void letsAFailedAddressCreateThrough() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(BAD_GATEWAY, "Licensed business is down"));

		assertThatThrownBy(() -> licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null)))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void failsWhenTheCreatedAddressCarriesNoLocation() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenReturn(ResponseEntity.status(CREATED).build());

		assertThatThrownBy(() -> licensedBusinessIntegration.findOrCreateAddress(MUNICIPALITY_ID, address(null)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	@Test
	void knowsWhetherANumberIsAtTheAddress() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.getAddressRestaurantNumbers(MUNICIPALITY_ID, addressId))
			.thenReturn(List.of(new AddressRestaurantNumber().number("22810001").status(AddressRestaurantNumber.StatusEnum.ACTIVE)));

		assertThat(licensedBusinessIntegration.isRestaurantNumberAtAddress(MUNICIPALITY_ID, addressId, "22810001")).isTrue();
		assertThat(licensedBusinessIntegration.isRestaurantNumberAtAddress(MUNICIPALITY_ID, addressId, "22810002")).isFalse();
	}

	@Test
	void answersWithTheAvailableNumbersInTheirOrder() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId))
			.thenReturn(List.of(restaurantNumber("22810001"), restaurantNumber("22810002")));

		assertThat(licensedBusinessIntegration.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).containsExactly("22810001", "22810002");
	}

	@Test
	void createsANumberAndAnswersWithItFromTheLocation() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.createRestaurantNumber(MUNICIPALITY_ID, addressId)).thenReturn(created("/2281/restaurant-numbers/22810002"));

		assertThat(licensedBusinessIntegration.createRestaurantNumber(MUNICIPALITY_ID, addressId)).isEqualTo("22810002");
	}

	@Test
	void readsTheIdOfANumber() {
		final var number = restaurantNumber("22810001");
		when(licensedBusinessClientMock.getRestaurantNumber(MUNICIPALITY_ID, "22810001")).thenReturn(Optional.of(number));

		assertThat(licensedBusinessIntegration.getRestaurantNumberId(MUNICIPALITY_ID, "22810001")).isEqualTo(number.getId());
	}

	@Test
	void failsWhenTheNumberCannotBeRead() {
		when(licensedBusinessClientMock.getRestaurantNumber(MUNICIPALITY_ID, "22810001")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> licensedBusinessIntegration.getRestaurantNumberId(MUNICIPALITY_ID, "22810001"))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("cannot be read");
	}

	@Test
	void findsTheIdOfAKnownAddressAndNothingForAnUnknownOne() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.of(address("address-id")), Optional.empty());

		assertThat(licensedBusinessIntegration.findAddressId(MUNICIPALITY_ID, address(null))).contains("address-id");
		assertThat(licensedBusinessIntegration.findAddressId(MUNICIPALITY_ID, address(null))).isEmpty();
	}

	@Test
	void answersWithTheActiveNumbersAtTheAddress() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.getAddressRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of(
			new AddressRestaurantNumber().number("22810001").status(AddressRestaurantNumber.StatusEnum.ACTIVE),
			new AddressRestaurantNumber().number("22810002").status(AddressRestaurantNumber.StatusEnum.AVAILABLE),
			new AddressRestaurantNumber().number("22810003").status(AddressRestaurantNumber.StatusEnum.ACTIVE)));

		assertThat(licensedBusinessIntegration.getActiveRestaurantNumbers(MUNICIPALITY_ID, addressId)).containsExactly("22810001", "22810003");
	}

	@Test
	void findsTheLatestAssignment() {
		final var assignment = new Assignment().id(randomUUID().toString());
		when(licensedBusinessClientMock.getLatestAssignment(MUNICIPALITY_ID, "22810001")).thenReturn(Optional.of(assignment));

		assertThat(licensedBusinessIntegration.findLatestAssignment(MUNICIPALITY_ID, "22810001")).contains(assignment);
	}

	@Test
	void createsTheAssignmentAsGiven() {
		final var assignment = new AssignmentCreateRequest().orgNumber("5566124144").validFrom(LocalDate.of(2026, 1, 1));

		licensedBusinessIntegration.createAssignment(MUNICIPALITY_ID, assignment);

		verify(licensedBusinessClientMock).createAssignment(MUNICIPALITY_ID, assignment);
	}

	private static Address address(final String id) {
		return new Address().id(id).streetAddress(STREET_ADDRESS).postalCode(POSTAL_CODE);
	}

	private static RestaurantNumber restaurantNumber(final String number) {
		return new RestaurantNumber().id(randomUUID().toString()).number(number);
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, "https://licensed-business.example.com" + location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
