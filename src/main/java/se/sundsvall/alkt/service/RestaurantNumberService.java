package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.licensedbusiness.LicensedBusinessIntegration;
import se.sundsvall.alkt.integration.party.PartyIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.isAssignedTo;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAddress;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAssignmentCreateRequest;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toValidFrom;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toNoPermitHolderMessage;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;

@Service
public class RestaurantNumberService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyIntegration partyIntegration;
	private final LicensedBusinessIntegration licensedBusinessIntegration;

	RestaurantNumberService(final SupportManagementIntegration supportManagementIntegration, final PartyIntegration partyIntegration,
		final LicensedBusinessIntegration licensedBusinessIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyIntegration = partyIntegration;
		this.licensedBusinessIntegration = licensedBusinessIntegration;
	}

	/**
	 * The number the case worker chose at the premises, a new one when they asked for it, and otherwise the premises' first
	 * free number or a new one when there is none. Nothing is assigned yet. Before a number is created the free numbers
	 * are handed to saveAvailableBeforeCreate, and a rerun gets them back as availableBeforeCreate, null on a first run.
	 */
	public String resolveRestaurantNumber(final String municipalityId, final String namespace, final String errandId, final List<String> availableBeforeCreate,
		final Consumer<List<String>> saveAvailableBeforeCreate) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		requireAssignableHolder(municipalityId, errand, errandId);
		final var parameters = toParameterValues(errand.getParameters());
		final var addressId = licensedBusinessIntegration.findOrCreateAddress(municipalityId, toAddress(parameters, errandId));

		return Optional.ofNullable(parameters.get(ERRAND_PARAMETER_RESTAURANT_NUMBER))
			.map(number -> requireAtAddress(municipalityId, addressId, number, errandId))
			.orElseGet(() -> newOrAvailableRestaurantNumber(municipalityId, addressId, parameters, availableBeforeCreate, saveAvailableBeforeCreate));
	}

	/**
	 * Assigns the number to the permit holder from the day the permit is issued. Licensed business ends an active one on
	 * the number, which is how an owner change at the premises works. Answers false when it was already assigned.
	 */
	public boolean assignRestaurantNumber(final String municipalityId, final String namespace, final String errandId, final String restaurantNumber) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var decision = supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has no completed decision to assign restaurant number '%s' by".formatted(errandId, restaurantNumber)));
		final var permitHolder = getPermitHolder(errand, errandId);
		final var holderName = getHolderName(permitHolder, errandId);
		final var orgNumber = partyIntegration.getLegalId(municipalityId, permitHolder.getExternalId());
		final var validFrom = toValidFrom(decision);

		// Why: a rerun after the assignment was created finds it here, and a second create would end the first.
		if (licensedBusinessIntegration.findLatestAssignment(municipalityId, restaurantNumber).filter(assignment -> isAssignedTo(assignment, orgNumber, validFrom)).isPresent()) {
			return false;
		}

		final var parameters = toParameterValues(errand.getParameters());
		final var addressId = licensedBusinessIntegration.findOrCreateAddress(municipalityId, toAddress(parameters, errandId));
		licensedBusinessIntegration.createAssignment(municipalityId, toAssignmentCreateRequest(licensedBusinessIntegration.getRestaurantNumberId(municipalityId, restaurantNumber),
			addressId, orgNumber, holderName, parameters, decision));
		return true;
	}

	// Why: the number is assigned only once the permit exists, so a holder it cannot be assigned to stops the process here,
	// before there is a permit without an assignment.
	private void requireAssignableHolder(final String municipalityId, final Errand errand, final String errandId) {
		final var permitHolder = getPermitHolder(errand, errandId);
		getHolderName(permitHolder, errandId);
		partyIntegration.getLegalId(municipalityId, permitHolder.getExternalId());
	}

	private String requireAtAddress(final String municipalityId, final String addressId, final String number, final String errandId) {
		if (!licensedBusinessIntegration.isRestaurantNumberAtAddress(municipalityId, addressId, number)) {
			throw new NonRetryableException("Restaurant number '%s' chosen in errand '%s' is not at the premises address".formatted(number, errandId));
		}
		return number;
	}

	private String newOrAvailableRestaurantNumber(final String municipalityId, final String addressId, final Map<String, String> parameters,
		final List<String> availableBeforeCreate, final Consumer<List<String>> saveAvailableBeforeCreate) {
		final var available = licensedBusinessIntegration.getAvailableRestaurantNumbers(municipalityId, addressId);
		if (!parseBoolean(parameters.get(ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER)) && !available.isEmpty()) {
			return available.getFirst();
		}

		return findCreatedByAnEarlierRun(available, availableBeforeCreate)
			.orElseGet(() -> {
				saveAvailableBeforeCreate.accept(available);
				return licensedBusinessIntegration.createRestaurantNumber(municipalityId, addressId);
			});
	}

	// Why: a run that fails after the create leaves its number free at the premises. It is the free number that was not
	// there before the create, so the rerun takes it instead of creating another.
	private static Optional<String> findCreatedByAnEarlierRun(final List<String> available, final List<String> availableBeforeCreate) {
		return Optional.ofNullable(availableBeforeCreate)
			.flatMap(before -> available.stream()
				.filter(number -> !before.contains(number))
				.findFirst());
	}

	private static Stakeholder getPermitHolder(final Errand errand, final String errandId) {
		return Optional.ofNullable(errand.getStakeholders()).orElse(emptyList()).stream()
			.filter(SupportManagementMapper::isPermitHolder)
			.findFirst()
			.orElseThrow(() -> new NonRetryableException(toNoPermitHolderMessage(errandId)));
	}

	private static String getHolderName(final Stakeholder permitHolder, final String errandId) {
		return Optional.ofNullable(permitHolder.getOrganizationName())
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> new NonRetryableException("The permit holder of errand '%s' has no organization name".formatted(errandId)));
	}
}
