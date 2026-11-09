package se.sundsvall.alkt.integration.licensedbusiness.mapper;

import generated.se.sundsvall.licensedbusiness.Address;
import generated.se.sundsvall.licensedbusiness.Assignment;
import generated.se.sundsvall.licensedbusiness.AssignmentCreateRequest;
import generated.se.sundsvall.licensedbusiness.LicenseHolder;
import generated.se.sundsvall.supportmanagement.Decision;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toFirstDay;
import static se.sundsvall.alkt.util.JsonText.text;

/** Maps the premises in the JSON parameter of the decision to what licensed business keeps of it. */
public final class LicensedBusinessMapper {

	private static final String ASSIGNMENT_STATUS_ACTIVE = "ACTIVE";
	private static final String PREMISES = "premises";
	private static final String RESTAURANT_NUMBER = "restaurantNumber";
	private static final String ADDRESS = "address";
	private static final String STREET = "street";
	private static final String POSTAL_CODE = "postalCode";
	private static final String CITY = "city";
	private static final List<String> ADDRESS_FIELDS = List.of(STREET, POSTAL_CODE, CITY);

	private LicensedBusinessMapper() {}

	/** The address of the premises in the JSON parameter of the decision, which must name all of it. */
	public static Address toAddress(final JsonNode decisionJson, final String errandId) {
		final var address = decisionJson.path(PREMISES).path(ADDRESS);
		final var missing = ADDRESS_FIELDS.stream()
			.filter(field -> text(address.path(field)).isEmpty())
			.map(field -> "%s.%s.%s".formatted(PREMISES, ADDRESS, field))
			.toList();
		if (!missing.isEmpty()) {
			throw new NonRetryableException("The decision of errand '%s' does not name all of the premises address, it lacks %s".formatted(errandId, String.join(", ", missing)));
		}

		return new Address()
			.streetAddress(text(address.path(STREET)).orElseThrow())
			.postalCode(text(address.path(POSTAL_CODE)).orElseThrow())
			.postalArea(text(address.path(CITY)).orElseThrow());
	}

	/**
	 * The restaurant number the case worker chose at the premises in the decision, if any. A decision that asks for a new
	 * number chose none, so a number beside the request is the one the process created for it.
	 */
	public static Optional<String> findChosenRestaurantNumber(final JsonNode decisionJson) {
		if (asksForANewRestaurantNumber(decisionJson)) {
			return Optional.empty();
		}
		return findRestaurantNumber(decisionJson);
	}

	/** The restaurant number at the premises in the decision, whoever put it there. */
	public static Optional<String> findRestaurantNumber(final JsonNode decisionJson) {
		return text(decisionJson.path(PREMISES).path(RESTAURANT_NUMBER));
	}

	/** A copy of the JSON parameter of the decision with the restaurant number at its premises, which must be an object. */
	public static JsonNode withRestaurantNumber(final JsonNode decisionJson, final String restaurantNumber) {
		final JsonNode copy = decisionJson.deepCopy();
		((ObjectNode) copy.get(PREMISES)).put(RESTAURANT_NUMBER, restaurantNumber);
		return copy;
	}

	/** Whether the case worker asked for a new restaurant number in the decision. */
	public static boolean asksForANewRestaurantNumber(final JsonNode decisionJson) {
		final var newRestaurantNumber = decisionJson.path(PREMISES).path("newRestaurantNumber");
		return newRestaurantNumber.isBoolean() && newRestaurantNumber.booleanValue();
	}

	public static AssignmentCreateRequest toAssignmentCreateRequest(final String restaurantNumberId, final String addressId, final String orgNumber,
		final String holderName, final JsonNode decisionJson, final Decision decision) {
		return new AssignmentCreateRequest()
			.restaurantNumberId(restaurantNumberId)
			.addressId(addressId)
			.orgNumber(orgNumber)
			.holderName(holderName)
			.premisesName(text(decisionJson.path(PREMISES).path("name")).orElse(null))
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
