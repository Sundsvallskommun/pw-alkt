package se.sundsvall.alkt.service;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.LicenseHolder;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.licensedbusiness.LicensedBusinessIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantNumberServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String ADDRESS_ID = "address-id";
	private static final String PARTY_ID = "party-id";
	private static final String ORG_NUMBER = "5566124144";
	private static final String NUMBER = "22810001";
	private static final String NUMBER_ID = "number-id";
	private static final LocalDate VALID_FROM = LocalDate.of(2026, 3, 1);

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyIntegration partyIntegrationMock;

	@Mock
	private LicensedBusinessIntegration licensedBusinessIntegrationMock;

	@Mock
	private Consumer<List<String>> saveAvailableBeforeCreateMock;

	@Captor
	private ArgumentCaptor<Address> addressCaptor;

	@Captor
	private ArgumentCaptor<AssignmentCreateRequest> assignmentCaptor;

	@InjectMocks
	private RestaurantNumberService restaurantNumberService;

	@AfterEach
	void verifyNoUnexpectedCalls() {
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyIntegrationMock, licensedBusinessIntegrationMock, saveAvailableBeforeCreateMock);
	}

	@Test
	void resolveTakesTheNumberTheCaseWorkerChoseAtThePremises() {
		givenResolvable(errand(parameter("restaurantNumber", NUMBER)));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(true);

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo(NUMBER);

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), addressCaptor.capture());
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
		assertThat(addressCaptor.getValue().getStreetAddress()).isEqualTo("Storgatan 33");
		assertThat(addressCaptor.getValue().getPostalCode()).isEqualTo("852 30");
		assertThat(addressCaptor.getValue().getPostalArea()).isEqualTo("Sundsvall");
	}

	/** A chosen number wins over a new one asked for. */
	@Test
	void resolveFailsWhenTheChosenNumberIsNotAtThePremises() {
		givenResolvable(errand(parameter("restaurantNumber", NUMBER), parameter("newRestaurantNumber", "true")));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(false);

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Restaurant number '22810001' chosen in errand 'errand-id' is not at the premises address");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"true", "TRUE"
	})
	void resolveCreatesANewNumberWhenTheCaseWorkerAskedForOneEvenWithAFreeOne(final String value) {
		givenResolvable(errand(parameter("newRestaurantNumber", value)));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER));
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo("22810002");

		verifyResolveRead();
		verify(saveAvailableBeforeCreateMock).accept(List.of(NUMBER));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	/** The earlier run created 22810002 and failed before the engine heard of it. */
	@Test
	void resolveTakesTheNewNumberAnEarlierRunCreatedInsteadOfCreatingAnother() {
		givenResolvable(errand(parameter("newRestaurantNumber", "true")));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, List.of(NUMBER), saveAvailableBeforeCreateMock))
			.isEqualTo("22810002");

		verifyResolveRead();
		verifyNoInteractions(saveAvailableBeforeCreateMock);
	}

	/** The earlier run failed before its create, so nothing new is free and the rerun creates the number. */
	@Test
	void resolveCreatesTheNewNumberWhenTheEarlierRunDidNot() {
		givenResolvable(errand(parameter("newRestaurantNumber", "true")));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER));
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, List.of(NUMBER), saveAvailableBeforeCreateMock))
			.isEqualTo("22810002");

		verifyResolveRead();
		verify(saveAvailableBeforeCreateMock).accept(List.of(NUMBER));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"false", "yes"
	})
	void resolveTakesTheFirstFreeNumberWithoutAChoice(final String value) {
		givenResolvable(errand(parameter("newRestaurantNumber", value)));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo(NUMBER);

		verifyResolveRead();
		verifyNoInteractions(saveAvailableBeforeCreateMock);
	}

	@Test
	void resolveCreatesANewNumberWhenThePremisesHaveNoFreeOne() {
		givenResolvable(errand());
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of());
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo("22810002");

		verifyResolveRead();
		verify(saveAvailableBeforeCreateMock).accept(List.of());
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	@Test
	void resolveFailsWithoutThePremisesAddress() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().parameters(List.of()));
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("has no premises address");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
	}

	/** The number is assigned only after the permit, so a holder it cannot be assigned to stops the process before it. */
	@Test
	void resolveFailsBeforeLicensedBusinessWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand().stakeholders(List.of()));

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void resolveFailsBeforeLicensedBusinessWhenThePermitHolderHasNoName() {
		final var errand = errand();
		errand.getStakeholders().getFirst().setOrganizationName(null);
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand 'errand-id' has no organization name");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void assignAssignsTheNumberToThePermitHolderAtThePremises() {
		givenErrand(errand());
		givenDecisionAndHolder();
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.empty());
		when(licensedBusinessIntegrationMock.getRestaurantNumberId(MUNICIPALITY_ID, NUMBER)).thenReturn(NUMBER_ID);

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER)).isTrue();

		verifyAssignmentRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).getRestaurantNumberId(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).createAssignment(eq(MUNICIPALITY_ID), assignmentCaptor.capture());
		assertThat(assignmentCaptor.getValue().getRestaurantNumberId()).isEqualTo(NUMBER_ID);
		assertThat(assignmentCaptor.getValue().getAddressId()).isEqualTo(ADDRESS_ID);
		assertThat(assignmentCaptor.getValue().getOrgNumber()).isEqualTo(ORG_NUMBER);
		assertThat(assignmentCaptor.getValue().getHolderName()).isEqualTo("Krogen AB");
		assertThat(assignmentCaptor.getValue().getPremisesName()).isEqualTo("Harrys Pub");
		assertThat(assignmentCaptor.getValue().getValidFrom()).isEqualTo(VALID_FROM);
	}

	/** An owner change: the number is held by the earlier owner, and licensed business ends that assignment. */
	@Test
	void assignAssignsANumberAnotherHolderHasNow() {
		givenErrand(errand());
		givenDecisionAndHolder();
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER))
			.thenReturn(Optional.of(new Assignment().licenseHolder(new LicenseHolder().orgNumber("5590001111")).validFrom(LocalDate.of(2020, 1, 1))));
		when(licensedBusinessIntegrationMock.getRestaurantNumberId(MUNICIPALITY_ID, NUMBER)).thenReturn(NUMBER_ID);

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER)).isTrue();

		verifyAssignmentRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).getRestaurantNumberId(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).createAssignment(eq(MUNICIPALITY_ID), any());
	}

	@Test
	void assignLeavesANumberAlreadyAssignedByAnEarlierRun() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenDecisionAndHolder();
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER))
			.thenReturn(Optional.of(new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).validFrom(VALID_FROM)));

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER)).isFalse();

		verifyAssignmentRead();
	}

	@Test
	void assignFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no completed decision to assign restaurant number '22810001' by");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void assignFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(new Decision().validFrom(VALID_FROM)));

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void assignFailsWhenThePermitHolderHasNoName() {
		final var errand = errand();
		errand.getStakeholders().getFirst().setOrganizationName(" ");
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(new Decision().validFrom(VALID_FROM)));

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand 'errand-id' has no organization name");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	private void givenErrand(final Errand errand) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(licensedBusinessIntegrationMock.findOrCreateAddress(eq(MUNICIPALITY_ID), any())).thenReturn(ADDRESS_ID);
	}

	private void givenDecisionAndHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(new Decision().validFrom(VALID_FROM)));
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
	}

	private void givenResolvable(final Errand errand) {
		givenErrand(errand);
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
	}

	private void verifyResolveRead() {
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID);
	}

	private void verifyAssignmentRead() {
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
	}

	private static Errand errand(final Parameter... extra) {
		final var parameters = new ArrayList<>(List.of(
			parameter("premisesName", "Harrys Pub"),
			parameter("premisesStreetAddress", "Storgatan 33"),
			parameter("premisesPostalCode", "852 30"),
			parameter("premisesPostalArea", "Sundsvall")));
		parameters.addAll(List.of(extra));
		return new Errand()
			.parameters(parameters)
			.stakeholders(new ArrayList<>(List.of(new Stakeholder().role("PRIMARY").externalId(PARTY_ID).organizationName("Krogen AB"))));
	}

	private static Parameter parameter(final String key, final String value) {
		return new Parameter().key(key).values(List.of(value));
	}
}
