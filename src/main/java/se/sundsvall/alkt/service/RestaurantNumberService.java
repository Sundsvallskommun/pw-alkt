package se.sundsvall.alkt.service;

import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.PermitJsonProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.licensedbusiness.LicensedBusinessIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper;
import se.sundsvall.alkt.service.model.ResolvedRestaurantNumber;
import se.sundsvall.alkt.service.model.RestaurantNumbersBeforeCreate;
import tools.jackson.databind.JsonNode;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.asksForANewRestaurantNumber;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.findChosenRestaurantNumber;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.isActiveFor;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.isAssignedTo;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAddress;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAssignmentCreateRequest;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toValidFrom;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;

/**
 * Resolves and assigns the restaurant number of a serving permit by the premises in the JSON parameter of its decision.
 */
@Service
public class RestaurantNumberService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyIntegration partyIntegration;
	private final LicensedBusinessIntegration licensedBusinessIntegration;
	private final PermitJsonProperties permitJsonProperties;

	RestaurantNumberService(final SupportManagementIntegration supportManagementIntegration, final PartyIntegration partyIntegration,
		final LicensedBusinessIntegration licensedBusinessIntegration, final PermitJsonProperties permitJsonProperties) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyIntegration = partyIntegration;
		this.licensedBusinessIntegration = licensedBusinessIntegration;
		this.permitJsonProperties = permitJsonProperties;
	}

	/**
	 * The number the case worker chose at the premises in the decision, a new one when the decision asks for it, and
	 * otherwise the premises' first free number or a new one when there is none. Nothing is assigned yet. Before a number
	 * is created the free numbers at the address are handed to saveBeforeCreate, and a rerun gets them back as
	 * beforeCreate, null on a first run. They count only at the address they were taken at, so a rerun after the premises
	 * moved creates its number anew.
	 */
	public ResolvedRestaurantNumber resolveRestaurantNumber(final String municipalityId, final String namespace, final String errandId,
		final RestaurantNumbersBeforeCreate beforeCreate, final Consumer<RestaurantNumbersBeforeCreate> saveBeforeCreate) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		requireAssignableHolder(municipalityId, errand, errandId);
		final var decisionJson = getDecisionJson(municipalityId, namespace, errandId, getCompletedDecision(municipalityId, namespace, errandId));
		final var premisesMunicipalityId = toPremisesMunicipalityId(errand);
		final var addressId = licensedBusinessIntegration.findOrCreateAddress(premisesMunicipalityId, toAddress(decisionJson, errandId));

		final var number = findChosenRestaurantNumber(decisionJson)
			.map(chosen -> requireAtAddress(premisesMunicipalityId, addressId, chosen, errandId))
			.orElseGet(() -> newOrAvailableRestaurantNumber(premisesMunicipalityId, addressId, decisionJson, beforeCreate, saveBeforeCreate));

		final var latestAssignment = licensedBusinessIntegration.findLatestAssignment(premisesMunicipalityId, number);
		return new ResolvedRestaurantNumber(number, addressId, toAssignmentId(latestAssignment));
	}

	/**
	 * The number the permit holder already holds at the premises, for a permit that runs beside the holder's permanent one.
	 * The case worker's choice in the decision must be one of them. Nothing is written to licensed business, as an
	 * assignment would end the one of the permanent permit.
	 */
	public String findRestaurantNumberOfHolder(final String municipalityId, final String namespace, final String errandId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var decisionJson = getDecisionJson(municipalityId, namespace, errandId, getCompletedDecision(municipalityId, namespace, errandId));
		final var orgNumber = partyIntegration.getLegalId(municipalityId, getPermitHolder(errand, errandId).getExternalId());
		final var premisesMunicipalityId = toPremisesMunicipalityId(errand);
		final var addressId = licensedBusinessIntegration.findAddressId(premisesMunicipalityId, toAddress(decisionJson, errandId))
			.orElseThrow(() -> new NonRetryableException("The premises address of errand '%s' is unknown to licensed business, so it has no restaurant number".formatted(errandId)));

		return findChosenRestaurantNumber(decisionJson)
			.map(number -> requireHeldBy(premisesMunicipalityId, addressId, number, orgNumber, errandId))
			.orElseGet(() -> findTheOnlyNumberHeldBy(premisesMunicipalityId, addressId, orgNumber, errandId));
	}

	public boolean assignRestaurantNumber(final String municipalityId, final String namespace, final String errandId, final String restaurantNumber, final String addressId,
		final String latestAssignmentIdSeen) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var decision = getCompletedDecision(municipalityId, namespace, errandId);
		final var permitHolder = getPermitHolder(errand, errandId);
		final var holderName = getHolderName(permitHolder, errandId);
		final var orgNumber = partyIntegration.getLegalId(municipalityId, permitHolder.getExternalId());
		final var validFrom = toValidFrom(decision);
		final var premisesMunicipalityId = toPremisesMunicipalityId(errand);

		final var latestAssignment = licensedBusinessIntegration.findLatestAssignment(premisesMunicipalityId, restaurantNumber);
		if (latestAssignment.filter(assignment -> isAssignedTo(assignment, orgNumber, validFrom)).isPresent()) {
			return false;
		}
		if (!toAssignmentId(latestAssignment).equals(Optional.ofNullable(latestAssignmentIdSeen).orElse(""))) {
			throw new NonRetryableException("Restaurant number '%s' was assigned by another errand after errand '%s' chose it, so two errands chose the same number"
				.formatted(restaurantNumber, errandId));
		}

		final var decisionJson = getDecisionJson(municipalityId, namespace, errandId, decision);
		final var restaurantNumberId = licensedBusinessIntegration.getRestaurantNumberId(premisesMunicipalityId, restaurantNumber);
		final var request = toAssignmentCreateRequest(restaurantNumberId, addressId, orgNumber, holderName, decisionJson, decision);
		licensedBusinessIntegration.createAssignment(premisesMunicipalityId, restaurantNumber, request);
		return true;
	}

	private Decision getCompletedDecision(final String municipalityId, final String namespace, final String errandId) {
		return supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has no completed decision to take the restaurant number from".formatted(errandId)));
	}

	private JsonNode getDecisionJson(final String municipalityId, final String namespace, final String errandId, final Decision decision) {
		return supportManagementIntegration.getDecisionJson(municipalityId, namespace, errandId, decision.getId(), permitJsonProperties.of(ALCOHOL).decisionKey());
	}

	private void requireAssignableHolder(final String municipalityId, final Errand errand, final String errandId) {
		final var permitHolder = getPermitHolder(errand, errandId);
		getHolderName(permitHolder, errandId);
		partyIntegration.getLegalId(municipalityId, permitHolder.getExternalId());
	}

	private String requireHeldBy(final String municipalityId, final String addressId, final String number, final String orgNumber, final String errandId) {
		if (!licensedBusinessIntegration.isRestaurantNumberAtAddress(municipalityId, addressId, number) || !isHeldNow(municipalityId, number, orgNumber)) {
			throw new NonRetryableException("Restaurant number '%s' chosen in errand '%s' is not held by the permit holder at the premises".formatted(number, errandId));
		}
		return number;
	}

	private String findTheOnlyNumberHeldBy(final String municipalityId, final String addressId, final String orgNumber, final String errandId) {
		final var numbers = licensedBusinessIntegration.getActiveRestaurantNumbers(municipalityId, addressId).stream()
			.filter(number -> isHeldNow(municipalityId, number, orgNumber))
			.toList();
		if (numbers.isEmpty()) {
			throw new NonRetryableException("The permit holder of errand '%s' holds no restaurant number at the premises".formatted(errandId));
		}
		if (numbers.size() > 1) {
			throw new NonRetryableException("The permit holder of errand '%s' holds %d restaurant numbers at the premises, one was expected. Choose one in the decision"
				.formatted(errandId, numbers.size()));
		}
		return numbers.getFirst();
	}

	private boolean isHeldNow(final String municipalityId, final String number, final String orgNumber) {
		return licensedBusinessIntegration.findLatestAssignment(municipalityId, number)
			.filter(assignment -> isActiveFor(assignment, orgNumber))
			.isPresent();
	}

	private String requireAtAddress(final String municipalityId, final String addressId, final String number, final String errandId) {
		if (!licensedBusinessIntegration.isRestaurantNumberAtAddress(municipalityId, addressId, number)) {
			throw new NonRetryableException("Restaurant number '%s' chosen in errand '%s' is not at the premises address".formatted(number, errandId));
		}
		return number;
	}

	private String newOrAvailableRestaurantNumber(final String municipalityId, final String addressId, final JsonNode decisionJson,
		final RestaurantNumbersBeforeCreate beforeCreate, final Consumer<RestaurantNumbersBeforeCreate> saveBeforeCreate) {
		final var available = licensedBusinessIntegration.getAvailableRestaurantNumbers(municipalityId, addressId);
		if (!asksForANewRestaurantNumber(decisionJson) && !available.isEmpty()) {
			return available.getFirst();
		}

		return findCreatedByAnEarlierRun(addressId, available, beforeCreate)
			.orElseGet(() -> {
				saveBeforeCreate.accept(new RestaurantNumbersBeforeCreate(addressId, available));
				return licensedBusinessIntegration.createRestaurantNumber(municipalityId, addressId);
			});
	}

	private static Optional<String> findCreatedByAnEarlierRun(final String addressId, final List<String> available, final RestaurantNumbersBeforeCreate beforeCreate) {
		return Optional.ofNullable(beforeCreate)
			.filter(before -> addressId.equals(before.addressId()))
			.flatMap(before -> available.stream()
				.filter(number -> !before.numbers().contains(number))
				.findFirst());
	}

	private static String toAssignmentId(final Optional<Assignment> assignment) {
		return assignment.map(Assignment::getId).orElse("");
	}

	private static Stakeholder getPermitHolder(final Errand errand, final String errandId) {
		return Optional.ofNullable(errand.getStakeholders()).orElse(emptyList()).stream()
			.filter(SupportManagementMapper::isPermitHolder)
			.findFirst()
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
	}

	private static String getHolderName(final Stakeholder permitHolder, final String errandId) {
		return Optional.ofNullable(permitHolder.getOrganizationName())
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> new NonRetryableException("The permit holder of errand '%s' has no organization name".formatted(errandId)));
	}
}
