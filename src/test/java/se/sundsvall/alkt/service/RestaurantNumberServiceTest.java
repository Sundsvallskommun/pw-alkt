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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.PermitJsonProperties;
import se.sundsvall.alkt.configuration.PermitJsonProperties.Settings;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.licensedbusiness.LicensedBusinessIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.ResolvedRestaurantNumber;
import se.sundsvall.alkt.service.model.RestaurantNumbersBeforeCreate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String DECISION_ID = "decision-id";
	private static final String DECISION_KEY = "aot_alcohol_decision";
	private static final String ADDRESS_ID = "address-id";
	private static final String PARTY_ID = "party-id";
	private static final String ORG_NUMBER = "5566124144";
	private static final String NUMBER = "22810001";
	private static final String NUMBER_ID = "number-id";
	private static final String ASSIGNMENT_ID = "assignment-id";
	private static final LocalDate VALID_FROM = LocalDate.of(2026, 3, 1);
	private static final Decision DECISION = new Decision().id(DECISION_ID).validFrom(VALID_FROM);

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyIntegration partyIntegrationMock;

	@Mock
	private LicensedBusinessIntegration licensedBusinessIntegrationMock;

	@Mock
	private Consumer<RestaurantNumbersBeforeCreate> saveAvailableBeforeCreateMock;

	@Captor
	private ArgumentCaptor<Address> addressCaptor;

	@Captor
	private ArgumentCaptor<AssignmentCreateRequest> assignmentCaptor;

	private RestaurantNumberService restaurantNumberService;

	@BeforeEach
	void setUp() {
		restaurantNumberService = new RestaurantNumberService(supportManagementIntegrationMock, partyIntegrationMock, licensedBusinessIntegrationMock,
			new PermitJsonProperties(new Settings("0.1", DECISION_KEY), new Settings("0.1", "aot_tobacco_decision")));
	}

	@AfterEach
	void verifyNoUnexpectedCalls() {
		verifyNoMoreInteractions(supportManagementIntegrationMock, partyIntegrationMock, licensedBusinessIntegrationMock, saveAvailableBeforeCreateMock);
	}

	/** The number is held, as in an owner change, so its assignment is noted for the assign step to compare with. */
	@Test
	void resolveTakesTheNumberTheCaseWorkerChoseAtThePremisesInTheDecision() {
		givenResolvable(errand(), premises("\"restaurantNumber\": \"22810001\""));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(true);
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(new Assignment().id(ASSIGNMENT_ID)));

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isEqualTo(new ResolvedRestaurantNumber(NUMBER, ADDRESS_ID, ASSIGNMENT_ID));

		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), addressCaptor.capture());
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
		assertThat(addressCaptor.getValue().getStreetAddress()).isEqualTo("Storgatan 33");
		assertThat(addressCaptor.getValue().getPostalCode()).isEqualTo("852 30");
		assertThat(addressCaptor.getValue().getPostalArea()).isEqualTo("Sundsvall");
	}

	/** A chosen number wins over a new one asked for. */
	@Test
	void resolveFailsWhenTheChosenNumberIsNotAtThePremises() {
		givenResolvable(errand(), premises("\"restaurantNumber\": \"22810001\"", "\"newRestaurantNumber\": true"));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(false);

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Restaurant number '22810001' chosen in errand 'errand-id' is not at the premises address");

		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
	}

	@Test
	void resolveCreatesANewNumberWhenTheCaseWorkerAskedForOneEvenWithAFreeOne() {
		givenResolvable(errand(), premises("\"newRestaurantNumber\": true"));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER));
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo(new ResolvedRestaurantNumber("22810002", ADDRESS_ID, ""));

		verifyResolveRead("22810002");
		verify(saveAvailableBeforeCreateMock).accept(new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of(NUMBER)));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	/** The earlier run created 22810002 and failed before the engine heard of it. */
	@Test
	void resolveTakesTheNewNumberAnEarlierRunCreatedInsteadOfCreatingAnother() {
		givenResolvable(errand(), premises("\"newRestaurantNumber\": true"));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of(NUMBER)), saveAvailableBeforeCreateMock))
			.isEqualTo(new ResolvedRestaurantNumber("22810002", ADDRESS_ID, ""));

		verifyResolveRead("22810002");
		verifyNoInteractions(saveAvailableBeforeCreateMock);
	}

	/**
	 * The earlier run was at the address the premises had before they were corrected, so a number free at this address is
	 * not one it created.
	 */
	@Test
	void resolveCreatesTheNewNumberWhenTheEarlierRunWasAtAnotherAddress() {
		givenResolvable(errand(), premises("\"newRestaurantNumber\": true"));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810003");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, new RestaurantNumbersBeforeCreate("other-address-id", List.of(NUMBER)),
			saveAvailableBeforeCreateMock)).isEqualTo(new ResolvedRestaurantNumber("22810003", ADDRESS_ID, ""));

		verifyResolveRead("22810003");
		verify(saveAvailableBeforeCreateMock).accept(new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of(NUMBER, "22810002")));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	/** The earlier run failed before its create, so nothing new is free and the rerun creates the number. */
	@Test
	void resolveCreatesTheNewNumberWhenTheEarlierRunDidNot() {
		givenResolvable(errand(), premises("\"newRestaurantNumber\": true"));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER));
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of(NUMBER)), saveAvailableBeforeCreateMock))
			.isEqualTo(new ResolvedRestaurantNumber("22810002", ADDRESS_ID, ""));

		verifyResolveRead("22810002");
		verify(saveAvailableBeforeCreateMock).accept(new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of(NUMBER)));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	/** Only the boolean true asks for a new number, and a blank choice is no choice. */
	@ParameterizedTest
	@ValueSource(strings = {
		"\"newRestaurantNumber\": false", "\"newRestaurantNumber\": \"true\"", "\"restaurantNumber\": \" \""
	})
	void resolveTakesTheFirstFreeNumberWithoutAChoice(final String field) {
		givenResolvable(errand(), premises(field));
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo(new ResolvedRestaurantNumber(NUMBER, ADDRESS_ID, ""));

		verifyResolveRead(NUMBER);
		verifyNoInteractions(saveAvailableBeforeCreateMock);
	}

	@Test
	void resolveCreatesANewNumberWhenThePremisesHaveNoFreeOne() {
		givenResolvable(errand(), premises());
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of());
		when(licensedBusinessIntegrationMock.createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn("22810002");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock)).isEqualTo(new ResolvedRestaurantNumber("22810002", ADDRESS_ID, ""));

		verifyResolveRead("22810002");
		verify(saveAvailableBeforeCreateMock).accept(new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of()));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber(MUNICIPALITY_ID, ADDRESS_ID);
	}

	@Test
	void resolveFailsWhenTheDecisionDoesNotNameAllOfThePremisesAddress() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		givenTheDecision(json("""
			{ "premises": { "name": "Harrys Pub", "address": { "street": "Storgatan 33", "city": " " } } }
			"""));

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The decision of errand 'errand-id' does not name all of the premises address, it lacks premises.address.postalCode, premises.address.city");

		verifyTheErrandAndTheDecisionRead();
	}

	@Test
	void resolveFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no completed decision to take the restaurant number from");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
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
	void findTakesTheChosenNumberTheHolderHoldsAtThePremises() {
		givenAKnownPremisesOf(errand(), premises("\"restaurantNumber\": \"22810001\""));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(true);
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(activeAssignmentOf("556612-4144")));

		assertThat(restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(NUMBER);

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
	}

	@Test
	void findFailsOnAChosenNumberThatIsNotAtThePremises() {
		givenAKnownPremisesOf(errand(), premises("\"restaurantNumber\": \"22810001\""));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(false);

		assertThatThrownBy(() -> restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Restaurant number '22810001' chosen in errand 'errand-id' is not held by the permit holder at the premises");

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
	}

	@Test
	void findFailsOnAChosenNumberAnotherHolderHolds() {
		givenAKnownPremisesOf(errand(), premises("\"restaurantNumber\": \"22810001\""));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER)).thenReturn(true);
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(activeAssignmentOf("5590001111")));

		assertThatThrownBy(() -> restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("is not held by the permit holder");

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress(MUNICIPALITY_ID, ADDRESS_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
	}

	/** The other active number at the premises belongs to another holder, and an ended assignment holds nothing. */
	@Test
	void findTakesTheOnlyNumberTheHolderHoldsAtThePremises() {
		givenAKnownPremisesOf(errand(), premises());
		when(licensedBusinessIntegrationMock.getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of("22810002", NUMBER, "22810003"));
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, "22810002")).thenReturn(Optional.of(activeAssignmentOf("5590001111")));
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(activeAssignmentOf(ORG_NUMBER)));
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, "22810003"))
			.thenReturn(Optional.of(new Assignment().licenseHolder(new LicenseHolder().orgNumber(ORG_NUMBER)).status("ENDED")));

		assertThat(restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(NUMBER);

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, "22810002");
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, "22810003");
	}

	@Test
	void findFailsWhenTheHolderHoldsSeveralNumbersAtThePremises() {
		givenAKnownPremisesOf(errand(), premises());
		when(licensedBusinessIntegrationMock.getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of(NUMBER, "22810002"));
		when(licensedBusinessIntegrationMock.findLatestAssignment(eq(MUNICIPALITY_ID), any())).thenReturn(Optional.of(activeAssignmentOf(ORG_NUMBER)));

		assertThatThrownBy(() -> restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand 'errand-id' holds 2 restaurant numbers at the premises, one was expected. Choose one in the decision");

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, "22810002");
	}

	@Test
	void findFailsWhenTheHolderHoldsNoNumberAtThePremises() {
		givenAKnownPremisesOf(errand(), premises());
		when(licensedBusinessIntegrationMock.getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand 'errand-id' holds no restaurant number at the premises");

		verifyFindRead();
		verify(licensedBusinessIntegrationMock).getActiveRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID);
	}

	/** An address licensed business does not know is not created, since the step writes nothing there. */
	@Test
	void findFailsWhenLicensedBusinessDoesNotKnowThePremises() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenTheDecision(premises());
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		when(licensedBusinessIntegrationMock.findAddressId(eq(MUNICIPALITY_ID), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The premises address of errand 'errand-id' is unknown to licensed business, so it has no restaurant number");

		verifyFindRead();
	}

	/**
	 * The number was free when it was chosen and still is. A process without the noted assignment counts as having seen
	 * none. The number is assigned at the address it was chosen at, which the decision is not asked for again.
	 */
	@ParameterizedTest
	@NullAndEmptySource
	void assignAssignsTheNumberToThePermitHolderAtTheAddressItWasChosenAt(final String latestAssignmentIdSeen) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenDecisionAndHolder();
		givenTheDecisionJson(premises());
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.empty());
		when(licensedBusinessIntegrationMock.getRestaurantNumberId(MUNICIPALITY_ID, NUMBER)).thenReturn(NUMBER_ID);

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, latestAssignmentIdSeen)).isTrue();

		verifyAssignmentRead();
		verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, DECISION_KEY);
		verify(licensedBusinessIntegrationMock).getRestaurantNumberId(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).createAssignment(eq(MUNICIPALITY_ID), eq(NUMBER), assignmentCaptor.capture());
		assertThat(assignmentCaptor.getValue().getRestaurantNumberId()).isEqualTo(NUMBER_ID);
		assertThat(assignmentCaptor.getValue().getAddressId()).isEqualTo(ADDRESS_ID);
		assertThat(assignmentCaptor.getValue().getOrgNumber()).isEqualTo(ORG_NUMBER);
		assertThat(assignmentCaptor.getValue().getHolderName()).isEqualTo("Krogen AB");
		assertThat(assignmentCaptor.getValue().getPremisesName()).isEqualTo("Harrys Pub");
		assertThat(assignmentCaptor.getValue().getValidFrom()).isEqualTo(VALID_FROM);
	}

	/**
	 * An owner change: the case worker ended the assignment of the earlier owner, which is still the latest on the number.
	 * The decision names no premises, so the assignment has no premises name.
	 */
	@Test
	void assignAssignsANumberAnotherHolderHasNow() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenDecisionAndHolder();
		givenTheDecisionJson(json("{}"));
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(assignmentOfAnotherHolder(ASSIGNMENT_ID)));
		when(licensedBusinessIntegrationMock.getRestaurantNumberId(MUNICIPALITY_ID, NUMBER)).thenReturn(NUMBER_ID);

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, ASSIGNMENT_ID)).isTrue();

		verifyAssignmentRead();
		verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, DECISION_KEY);
		verify(licensedBusinessIntegrationMock).getRestaurantNumberId(MUNICIPALITY_ID, NUMBER);
		verify(licensedBusinessIntegrationMock).createAssignment(eq(MUNICIPALITY_ID), eq(NUMBER), assignmentCaptor.capture());
		assertThat(assignmentCaptor.getValue().getPremisesName()).isNull();
	}

	/** Another errand chose the number in the meantime and assigned it first, whether it was free or held when chosen. */
	@ParameterizedTest
	@ValueSource(strings = {
		"", ASSIGNMENT_ID
	})
	void assignFailsWhenAnotherErrandAssignedTheNumberFirst(final String latestAssignmentIdSeen) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenDecisionAndHolder();
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER)).thenReturn(Optional.of(assignmentOfAnotherHolder("other-assignment-id")));

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, latestAssignmentIdSeen))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Restaurant number '22810001' was assigned by another errand after errand 'errand-id' chose it, so two errands chose the same number");

		verifyAssignmentRead();
	}

	@Test
	void assignLeavesANumberAlreadyAssignedByAnEarlierRun() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		givenDecisionAndHolder();
		when(licensedBusinessIntegrationMock.findLatestAssignment(MUNICIPALITY_ID, NUMBER))
			.thenReturn(Optional.of(new Assignment().licenseHolder(new LicenseHolder().orgNumber("556612-4144")).validFrom(VALID_FROM)));

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, "")).isFalse();

		verifyAssignmentRead();
	}

	@Test
	void assignFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand());
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, ""))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no completed decision to take the restaurant number from");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	@Test
	void assignFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(DECISION));

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, ""))
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
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(DECISION));

		assertThatThrownBy(() -> restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, NUMBER, ADDRESS_ID, ""))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The permit holder of errand 'errand-id' has no organization name");

		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	}

	/**
	 * Licensed business numbers a premises by its municipality, so a premises in Timrå gets a number starting with 2262.
	 */
	@Test
	void resolveCreatesTheNumberOfAPremisesInTimraInTimra() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand(parameter("premisesMunicipalityId", "2262")));
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		givenTheDecision(premises());
		when(licensedBusinessIntegrationMock.findOrCreateAddress(eq("2262"), any())).thenReturn(ADDRESS_ID);
		when(licensedBusinessIntegrationMock.getAvailableRestaurantNumbers("2262", ADDRESS_ID)).thenReturn(List.of());
		when(licensedBusinessIntegrationMock.createRestaurantNumber("2262", ADDRESS_ID)).thenReturn("22620001");

		assertThat(restaurantNumberService.resolveRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null, saveAvailableBeforeCreateMock))
			.isEqualTo(new ResolvedRestaurantNumber("22620001", ADDRESS_ID, ""));

		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq("2262"), any());
		verify(licensedBusinessIntegrationMock).getAvailableRestaurantNumbers("2262", ADDRESS_ID);
		verify(saveAvailableBeforeCreateMock).accept(new RestaurantNumbersBeforeCreate(ADDRESS_ID, List.of()));
		verify(licensedBusinessIntegrationMock).createRestaurantNumber("2262", ADDRESS_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment("2262", "22620001");
	}

	@Test
	void findLooksUpTheNumberOfAPremisesInTimraInTimra() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand(parameter("premisesMunicipalityId", "2262")));
		givenTheDecision(premises("\"restaurantNumber\": \"22620001\""));
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		when(licensedBusinessIntegrationMock.findAddressId(eq("2262"), any())).thenReturn(Optional.of(ADDRESS_ID));
		when(licensedBusinessIntegrationMock.isRestaurantNumberAtAddress("2262", ADDRESS_ID, "22620001")).thenReturn(true);
		when(licensedBusinessIntegrationMock.findLatestAssignment("2262", "22620001")).thenReturn(Optional.of(activeAssignmentOf("556612-4144")));

		assertThat(restaurantNumberService.findRestaurantNumberOfHolder(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("22620001");

		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findAddressId(eq("2262"), any());
		verify(licensedBusinessIntegrationMock).isRestaurantNumberAtAddress("2262", ADDRESS_ID, "22620001");
		verify(licensedBusinessIntegrationMock).findLatestAssignment("2262", "22620001");
	}

	@Test
	void assignAssignsTheNumberOfAPremisesInTimraInTimra() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand(parameter("premisesMunicipalityId", "2262")));
		givenTheDecision(premises());
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		when(licensedBusinessIntegrationMock.findLatestAssignment("2262", "22620001")).thenReturn(Optional.empty());
		when(licensedBusinessIntegrationMock.getRestaurantNumberId("2262", "22620001")).thenReturn(NUMBER_ID);

		assertThat(restaurantNumberService.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22620001", ADDRESS_ID, null)).isTrue();

		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findLatestAssignment("2262", "22620001");
		verify(licensedBusinessIntegrationMock).getRestaurantNumberId("2262", "22620001");
		verify(licensedBusinessIntegrationMock).createAssignment(eq("2262"), eq("22620001"), any());
	}

	private void givenDecisionAndHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(DECISION));
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
	}

	private void givenTheDecision(final JsonNode decisionJson) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(DECISION));
		givenTheDecisionJson(decisionJson);
	}

	private void givenTheDecisionJson(final JsonNode decisionJson) {
		when(supportManagementIntegrationMock.getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, DECISION_KEY)).thenReturn(decisionJson);
	}

	private void givenAKnownPremisesOf(final Errand errand, final JsonNode decisionJson) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		givenTheDecision(decisionJson);
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		when(licensedBusinessIntegrationMock.findAddressId(eq(MUNICIPALITY_ID), any())).thenReturn(Optional.of(ADDRESS_ID));
	}

	private void givenResolvable(final Errand errand, final JsonNode decisionJson) {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(partyIntegrationMock.getLegalId(MUNICIPALITY_ID, PARTY_ID)).thenReturn(ORG_NUMBER);
		givenTheDecision(decisionJson);
		when(licensedBusinessIntegrationMock.findOrCreateAddress(eq(MUNICIPALITY_ID), any())).thenReturn(ADDRESS_ID);
	}

	private void verifyTheErrandAndTheDecisionRead() {
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getDecisionJson(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, DECISION_KEY);
	}

	private void verifyFindRead() {
		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findAddressId(eq(MUNICIPALITY_ID), any());
	}

	private void verifyResolveRead(final String number) {
		verifyTheErrandAndTheDecisionRead();
		verify(licensedBusinessIntegrationMock).findOrCreateAddress(eq(MUNICIPALITY_ID), any());
		verify(licensedBusinessIntegrationMock).getAvailableRestaurantNumbers(MUNICIPALITY_ID, ADDRESS_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, number);
	}

	private void verifyAssignmentRead() {
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(partyIntegrationMock).getLegalId(MUNICIPALITY_ID, PARTY_ID);
		verify(licensedBusinessIntegrationMock).findLatestAssignment(MUNICIPALITY_ID, NUMBER);
	}

	private static Assignment assignmentOfAnotherHolder(final String id) {
		return new Assignment().id(id).licenseHolder(new LicenseHolder().orgNumber("5590001111")).validFrom(LocalDate.of(2020, 1, 1)).validTo(LocalDate.of(2026, 2, 28));
	}

	private static Assignment activeAssignmentOf(final String orgNumber) {
		return new Assignment().licenseHolder(new LicenseHolder().orgNumber(orgNumber)).status("ACTIVE");
	}

	/** The JSON parameter of a decision whose premises has a name, a full address and the fields given. */
	private static JsonNode premises(final String... fields) {
		final var extra = fields.length == 0 ? "" : ", " + String.join(", ", fields);
		return json("""
			{ "premises": { "name": "Harrys Pub", "address": { "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" }%s } }
			""".formatted(extra));
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}

	private static Errand errand(final Parameter... parameters) {
		return new Errand()
			.parameters(new ArrayList<>(List.of(parameters)))
			.stakeholders(new ArrayList<>(List.of(new Stakeholder().role("PRIMARY").externalId(PARTY_ID).organizationName("Krogen AB"))));
	}

	private static Parameter parameter(final String key, final String value) {
		return new Parameter().key(key).values(List.of(value));
	}
}
