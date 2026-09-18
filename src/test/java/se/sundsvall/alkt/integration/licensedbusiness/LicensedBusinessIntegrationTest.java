package se.sundsvall.alkt.integration.licensedbusiness;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.RestaurantNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
	private static final String ORG_NUMBER = "5566124144";
	private static final String HOLDER_NAME = "Krogen AB";
	private static final String STREET_ADDRESS = "Storgatan 1";
	private static final String POSTAL_CODE = "852 30";

	@Mock
	private LicensedBusinessClient licensedBusinessClientMock;

	@InjectMocks
	private LicensedBusinessIntegration licensedBusinessIntegration;

	@Test
	void assignsAFreeNumberOnAnAddressThatIsAlreadyKnown() {
		final var addressId = randomUUID().toString();
		final var number = restaurantNumber("1001");
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.of(address(addressId)));
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of(number));

		assertThat(licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment())).isEqualTo("1001");

		final var assignmentCaptor = ArgumentCaptor.forClass(AssignmentCreateRequest.class);
		verify(licensedBusinessClientMock).createAssignment(eq(MUNICIPALITY_ID), assignmentCaptor.capture());
		assertThat(assignmentCaptor.getValue().getRestaurantNumberId()).isEqualTo(number.getId());
		assertThat(assignmentCaptor.getValue().getAddressId()).isEqualTo(addressId);
		assertThat(assignmentCaptor.getValue().getOrgNumber()).isEqualTo(ORG_NUMBER);
		assertThat(assignmentCaptor.getValue().getHolderName()).isEqualTo(HOLDER_NAME);
		verify(licensedBusinessClientMock, never()).createAddress(any(), any());
		verify(licensedBusinessClientMock, never()).createRestaurantNumber(any(), any());
	}

	@Test
	void createsTheAddressWhenTheLookupFindsNone() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenReturn(created("/2281/addresses/" + addressId));
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of(restaurantNumber("1001")));

		assertThat(licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment())).isEqualTo("1001");
	}

	/** Two errands on the same address race: the one that loses reads the address the other just created. */
	@Test
	void readsTheAddressAgainWhenTheCreateIsRefusedAsADuplicate() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE))
			.thenReturn(Optional.empty(), Optional.of(address(addressId)));
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(CONFLICT, "Address already exists"));
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of(restaurantNumber("1001")));

		assertThat(licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment())).isEqualTo("1001");
	}

	/** The duplicate check and the lookup are meant to share a key, so this pair of answers means they do not. */
	@Test
	void failsWhenTheDuplicateIsRefusedButStillCannotBeFound() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(CONFLICT, "Address already exists"));

		assertThatThrownBy(() -> licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment()))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("does not know it");
	}

	@Test
	void letsAFailedAddressCreateThrough() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenThrow(new ClientProblem(BAD_GATEWAY, "Licensed business is down"));

		assertThatThrownBy(() -> licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment()))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void allocatesANewNumberWhenTheAddressHasNoFreeOne() {
		final var addressId = randomUUID().toString();
		final var created = restaurantNumber("1002");
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.of(address(addressId)));
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of());
		when(licensedBusinessClientMock.createRestaurantNumber(MUNICIPALITY_ID, addressId)).thenReturn(created("/2281/restaurant-numbers/1002"));
		when(licensedBusinessClientMock.getRestaurantNumber(MUNICIPALITY_ID, "1002")).thenReturn(Optional.of(created));

		assertThat(licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment())).isEqualTo("1002");

		final var assignmentCaptor = ArgumentCaptor.forClass(AssignmentCreateRequest.class);
		verify(licensedBusinessClientMock).createAssignment(eq(MUNICIPALITY_ID), assignmentCaptor.capture());
		assertThat(assignmentCaptor.getValue().getRestaurantNumberId()).isEqualTo(created.getId());
	}

	/** The assignment takes the id, and Location only carries the number, so the created number has to be read back. */
	@Test
	void failsWhenTheNewNumberCannotBeReadBack() {
		final var addressId = randomUUID().toString();
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.of(address(addressId)));
		when(licensedBusinessClientMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, addressId)).thenReturn(List.of());
		when(licensedBusinessClientMock.createRestaurantNumber(MUNICIPALITY_ID, addressId)).thenReturn(created("/2281/restaurant-numbers/1002"));
		when(licensedBusinessClientMock.getRestaurantNumber(MUNICIPALITY_ID, "1002")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment()))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("cannot be read back");

		verify(licensedBusinessClientMock, never()).createAssignment(any(), any());
	}

	@Test
	void failsWhenTheCreatedAddressCarriesNoLocation() {
		when(licensedBusinessClientMock.lookupAddress(MUNICIPALITY_ID, STREET_ADDRESS, POSTAL_CODE)).thenReturn(Optional.empty());
		when(licensedBusinessClientMock.createAddress(eq(MUNICIPALITY_ID), any())).thenReturn(ResponseEntity.status(CREATED).build());

		assertThatThrownBy(() -> licensedBusinessIntegration.assignRestaurantNumber(MUNICIPALITY_ID, address(null), assignment()))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	private static AssignmentCreateRequest assignment() {
		return new AssignmentCreateRequest()
			.orgNumber(ORG_NUMBER)
			.holderName(HOLDER_NAME)
			.premisesName("Harrys Pub")
			.validFrom(LocalDate.of(2026, 1, 1));
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
