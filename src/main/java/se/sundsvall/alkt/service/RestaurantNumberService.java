package se.sundsvall.alkt.service;

import generated.se.sundsvall.licensedbusiness.Assignment;
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
import se.sundsvall.alkt.service.model.ResolvedRestaurantNumber;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.isActiveFor;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.isAssignedTo;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAddress;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toAssignmentCreateRequest;
import static se.sundsvall.alkt.integration.licensedbusiness.mapper.LicensedBusinessMapper.toValidFrom;
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
	public ResolvedRestaurantNumber resolveRestaurantNumber(final String municipalityId, final String namespace, final String errandId, final List<String> availableBeforeCreate,
		final Consumer<List<String>> saveAvailableBeforeCreate) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		requireAssignableHolder(municipalityId, errand, errandId);
		final var parameters = toParameterValues(errand.getParameters());
		final var addressId = licensedBusinessIntegration.findOrCreateAddress(municipalityId, toAddress(parameters, errandId));

		final var number = Optional.ofNullable(parameters.get(ERRAND_PARAMETER_RESTAURANT_NUMBER))
			.map(chosen -> requireAtAddress(municipalityId, addressId, chosen, errandId))
			.orElseGet(() -> newOrAvailableRestaurantNumber(municipalityId, addressId, parameters, availableBeforeCreate, saveAvailableBeforeCreate));

		final var latestAssignment = licensedBusinessIntegration.findLatestAssignment(municipalityId, number);
		return new ResolvedRestaurantNumber(number, toAssignmentId(latestAssignment));
	}

	/**
	 * The number the permit holder already holds at the premises, for a permit that runs beside the holder's permanent one.
	 * The case worker's choice must be one of them. Nothing is written to licensed business, as an assignment would end
	 * the one of the permanent permit.
	 */
	public String findRestaurantNumberOfHolder(final String municipalityId, final String namespace, final String errandId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var parameters = toParameterValues(errand.getParameters());
		final var orgNumber = partyIntegration.getLegalId(municipalityId, getPermitHolder(errand, errandId).getExternalId());
		final var addressId = licensedBusinessIntegration.findAddressId(municipalityId, toAddress(parameters, errandId))
			.orElseThrow(() -> new NonRetryableException("The premises address of errand '%s' is unknown to licensed business, so it has no restaurant number".formatted(errandId)));

		return Optional.ofNullable(parameters.get(ERRAND_PARAMETER_RESTAURANT_NUMBER))
			.map(number -> requireHeldBy(municipalityId, addressId, number, orgNumber, errandId))
			.orElseGet(() -> findTheOnlyNumberHeldBy(municipalityId, addressId, orgNumber, errandId));
	}

	/**
	 * Assigns the number to the permit holder from the day the permit is issued. Licensed business ends an active one on
	 * the number, which is how an owner change at the premises works. Answers false when it was already assigned, and
	 * throws when another errand assigned the number after this one chose it.
	 */
	public boolean assignRestaurantNumber(final String municipalityId, final String namespace, final String errandId, final String restaurantNumber, final String latestAssignmentIdSeen) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var decision = supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has no completed decision to assign restaurant number '%s' by".formatted(errandId, restaurantNumber)));
		final var permitHolder = getPermitHolder(errand, errandId);
		final var holderName = getHolderName(permitHolder, errandId);
		final var orgNumber = partyIntegration.getLegalId(municipalityId, permitHolder.getExternalId());
		final var validFrom = toValidFrom(decision);

		final var latestAssignment = licensedBusinessIntegration.findLatestAssignment(municipalityId, restaurantNumber);
		// Why: a step that failed after creating the assignment finds it on its rerun as the number's latest assignment.
		// Creating it again would end the one already made, since licensed business ends the active one on a new assignment.
		if (latestAssignment.filter(assignment -> isAssignedTo(assignment, orgNumber, validFrom)).isPresent()) {
			return false;
		}
		// Why: licensed business cannot reserve a number, so another errand may have assigned the same number after this one
		// chose it. Assigning it now would end that errand's assignment.
		if (!toAssignmentId(latestAssignment).equals(Optional.ofNullable(latestAssignmentIdSeen).orElse(""))) {
			throw new NonRetryableException("Restaurant number '%s' was assigned by another errand after errand '%s' chose it, so two errands chose the same number"
				.formatted(restaurantNumber, errandId));
		}

		final var parameters = toParameterValues(errand.getParameters());
		final var addressId = licensedBusinessIntegration.findOrCreateAddress(municipalityId, toAddress(parameters, errandId));
		final var restaurantNumberId = licensedBusinessIntegration.getRestaurantNumberId(municipalityId, restaurantNumber);
		final var request = toAssignmentCreateRequest(restaurantNumberId, addressId, orgNumber, holderName, parameters, decision);
		licensedBusinessIntegration.createAssignment(municipalityId, request);
		return true;
	}

	// Why: the number is assigned only once the permit exists, so a holder it cannot be assigned to stops the process here,
	// before there is a permit without an assignment.
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
			throw new NonRetryableException("The permit holder of errand '%s' holds %d restaurant numbers at the premises, one was expected. Choose one with the parameter '%s'"
				.formatted(errandId, numbers.size(), ERRAND_PARAMETER_RESTAURANT_NUMBER));
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
