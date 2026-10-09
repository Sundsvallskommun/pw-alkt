package se.sundsvall.alkt.integration.licensedbusiness.mapper;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.LicenseHolder;
import generated.se.sundsvall.supportmanagement.Decision;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;

import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_NAME;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_AREA;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_CODE;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_STREET_ADDRESS;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toFirstDay;

public final class LicensedBusinessMapper {

	private static final String ASSIGNMENT_STATUS_ACTIVE = "ACTIVE";
	private static final List<String> ADDRESS_PARAMETERS = List.of(ERRAND_PARAMETER_PREMISES_STREET_ADDRESS, ERRAND_PARAMETER_PREMISES_POSTAL_CODE,
		ERRAND_PARAMETER_PREMISES_POSTAL_AREA);

	private LicensedBusinessMapper() {}

	/** The address of the premises from the parameters of the errand, which must name all of it. */
	public static Address toAddress(final Map<String, String> errandParameters, final String errandId) {
		final var missing = ADDRESS_PARAMETERS.stream()
			.filter(key -> !errandParameters.containsKey(key))
			.toList();
		if (!missing.isEmpty()) {
			throw new NonRetryableException("Errand '%s' has no premises address, parameters %s are missing".formatted(errandId, missing));
		}

		return new Address()
			.streetAddress(errandParameters.get(ERRAND_PARAMETER_PREMISES_STREET_ADDRESS))
			.postalCode(errandParameters.get(ERRAND_PARAMETER_PREMISES_POSTAL_CODE))
			.postalArea(errandParameters.get(ERRAND_PARAMETER_PREMISES_POSTAL_AREA));
	}

	public static AssignmentCreateRequest toAssignmentCreateRequest(final String restaurantNumberId, final String addressId, final String orgNumber,
		final String holderName, final Map<String, String> errandParameters, final Decision decision) {
		return new AssignmentCreateRequest()
			.restaurantNumberId(restaurantNumberId)
			.addressId(addressId)
			.orgNumber(orgNumber)
			.holderName(holderName)
			.premisesName(errandParameters.get(ERRAND_PARAMETER_PREMISES_NAME))
			.validFrom(toValidFrom(decision))
			.validTo(decision.getValidTo());
	}

	/** The assignment starts the day the permit is issued: its first valid day, or else the day it was decided. */
	public static LocalDate toValidFrom(final Decision decision) {
		return toFirstDay(decision)
			.orElseThrow(() -> new NonRetryableException("Decision %s has neither validFrom nor decidedAt to start the assignment by".formatted(decision.getId())));
	}

	public static boolean isAssignedTo(final Assignment assignment, final String orgNumber, final LocalDate validFrom) {
		return isHeldBy(assignment, orgNumber) && Objects.equals(assignment.getValidFrom(), validFrom);
	}

	public static boolean isActiveFor(final Assignment assignment, final String orgNumber) {
		return isHeldBy(assignment, orgNumber) && ASSIGNMENT_STATUS_ACTIVE.equals(assignment.getStatus());
	}

	// Why: Party gives a personal identity number with its century, licensed business may keep it without, and an
	// organisation number may carry the prefix 16. The last ten digits name the same holder in every form.
	private static boolean isHeldBy(final Assignment assignment, final String orgNumber) {
		return Optional.ofNullable(assignment.getLicenseHolder())
			.map(LicenseHolder::getOrgNumber)
			.map(LicensedBusinessMapper::toTenDigits)
			.filter(toTenDigits(orgNumber)::equals)
			.isPresent();
	}

	private static String toTenDigits(final String orgNumber) {
		return StringUtils.right(orgNumber.replaceAll("\\D", ""), 10);
	}
}
