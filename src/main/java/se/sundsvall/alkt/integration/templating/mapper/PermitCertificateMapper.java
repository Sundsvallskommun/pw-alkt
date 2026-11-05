package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.model.PermitSchema;
import tools.jackson.databind.JsonNode;

import static java.util.stream.Collectors.joining;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;

/**
 * Maps the JSON parameter of a permit to the placeholders of its certificate: serving-permit-certificate for the
 * alcohol schema and tobacco-permit-certificate for the tobacco schema. A value the permit does not have is left out,
 * so
 * the strict template answers 400 naming it instead of rendering an empty field. Only conditions and information, which
 * a
 * permit may well have none of, become empty text, and a box is ticked or not.
 */
public final class PermitCertificateMapper {

	private static final Map<String, String> AUDIENCES = Map.of("PUBLIC", "Allmänheten", "CLOSED_COMPANY", "Slutet sällskap");
	private static final Map<String, String> LOCATIONS = Map.of("INDOOR", "inomhus", "OUTDOOR", "uteservering");
	private static final String ADDRESS = "address";
	private static final String ANNUAL_PERIOD = "ANNUAL_PERIOD";
	private static final String CONDITIONS = "conditions";
	private static final String DECISION_DATE = "decisionDate";
	private static final String DESCRIPTION = "description";
	private static final String ISSUED_BY = "issuedBy";
	private static final String PERIOD = "period";
	private static final String RANGE = " – ";
	private static final String VALID_FROM = "validFrom";

	private PermitCertificateMapper() {}

	/**
	 * @param  permit                the JSON parameter of the permit, the change already merged in
	 * @param  decision              the decision that grants or changes the permit
	 * @param  caseNumber            the case number of the deciding errand, which the certificate shows as its Dnr
	 * @throws NonRetryableException when the decision is an approval with conditions and the permit has none
	 */
	public static Map<String, Object> toCertificateParameters(final PermitSchema schema, final JsonNode permit, final Decision decision, final String caseNumber) {
		if (DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS.equals(decision.getOutcome()) && strings(permit.path(CONDITIONS)).isEmpty()) {
			throw new NonRetryableException("Decision %s is an approval with conditions but has no conditions, so no certificate is made".formatted(decision.getId()));
		}
		final var dnr = Optional.ofNullable(caseNumber).filter(StringUtils::isNotBlank);
		return switch (schema) {
			case ALCOHOL -> toServingPermitCertificate(permit, dnr);
			case TOBACCO -> toTobaccoPermitCertificate(permit, dnr);
		};
	}

	private static Map<String, Object> toServingPermitCertificate(final JsonNode permit, final Optional<String> caseNumber) {
		final var parameters = new LinkedHashMap<String, Object>();
		final var decision = permit.path("decision");
		final var holder = permit.path("permitHolder");
		final var premises = permit.path("premises");
		final var servingArea = permit.path("servingArea");
		final var periods = permit.path("servingPeriods").values();
		final var beverages = strings(permit.path("beverages"));
		final var servingTo = strings(permit.path("servingTo"));
		final var servingHours = permit.path("servingHours").values().stream()
			.map(PermitCertificateMapper::toServingHour)
			.flatMap(Optional::stream)
			.toList();

		putDecision(parameters, decision, caseNumber);
		put(parameters, "decisionDateAndCaseNumber", join(" / ", text(decision.path(DECISION_DATE)), caseNumber));
		put(parameters, VALID_FROM, text(decision.path(VALID_FROM)));
		putPermitHolder(parameters, holder);
		put(parameters, "permitHolderPhone", text(holder.path("phoneNumber")));
		putPlace(parameters, premises);
		put(parameters, "premisesRestaurantNumber", text(premises.path("restaurantNumber")));
		put(parameters, "servingAreaDescription", text(servingArea.path(DESCRIPTION)));
		put(parameters, "servingAreaDrawingReference", text(servingArea.path("drawingReference")));
		put(parameters, "servingAreaMaxPersons", text(servingArea.path("maxPersons")));
		put(parameters, "servingAreaSeats", text(servingArea.path("seatsIndoor")));
		parameters.put("scopeAllYear", box(periods.stream().anyMatch(period -> is(period.path(PERIOD), "ALL_YEAR"))));
		parameters.put("scopeAnnualPeriod", box(periods.stream().anyMatch(period -> is(period.path(PERIOD), ANNUAL_PERIOD))));
		put(parameters, "scopePeriodFromTo", joinAll(", ", periods.stream().map(PermitCertificateMapper::toPeriod)));
		parameters.put("scopeTasting", box(is(permit.path("tasting"), "true") || is(permit.path("servingPermitType"), "TASTING")));
		parameters.put("beverageBeer", box(beverages.contains("STRONG_BEER")));
		parameters.put("beverageWine", box(beverages.contains("WINE")));
		parameters.put("beverageOtherFermented", box(beverages.contains("OTHER_FERMENTED_BEVERAGES")));
		parameters.put("beverageSpirits", box(beverages.contains("SPIRITS")));
		parameters.put("servingToPublic", box(servingTo.contains("PUBLIC")));
		parameters.put("servingToClosedCompany", box(servingTo.contains("CLOSED_COMPANY")));
		put(parameters, "servingHours", joinAll("\n", servingHours.stream().map(Optional::of)));
		parameters.put(CONDITIONS, String.join("\n", strings(permit.path(CONDITIONS))));
		return parameters;
	}

	private static Map<String, Object> toTobaccoPermitCertificate(final JsonNode permit, final Optional<String> caseNumber) {
		final var parameters = new LinkedHashMap<String, Object>();
		final var decision = permit.path("decision");
		final var holder = permit.path("permitHolder");
		final var pointOfSale = permit.path("pointOfSale");
		final var salesPeriod = permit.path("salesPeriod");
		final var indefinite = is(salesPeriod.path(PERIOD), "INDEFINITE");
		final var temporary = is(salesPeriod.path(PERIOD), "TEMPORARY");
		final var salesTypes = strings(permit.path("salesTypes"));

		putDecision(parameters, decision, caseNumber);
		put(parameters, "decisionOfficerAndCaseNumber", join(", ", text(decision.path(ISSUED_BY).path("name")), caseNumber));
		putPermitHolder(parameters, holder);
		put(parameters, "permitHolderCareOf", text(holder.path(ADDRESS).path("careOf")));
		parameters.put("validityIndefinite", box(indefinite));
		parameters.put("validityTemporary", box(temporary));
		put(parameters, VALID_FROM, text(decision.path(VALID_FROM)));
		if (temporary) {
			put(parameters, "temporaryPeriod", join(RANGE, text(salesPeriod.path("from")), text(salesPeriod.path("to"))));
		}
		putPlace(parameters, pointOfSale);
		put(parameters, "premisesPropertyDesignation", text(pointOfSale.path("propertyDesignation")));
		parameters.put("salesRetail", box(salesTypes.contains("RETAIL")));
		parameters.put("salesWholesale", box(salesTypes.contains("WHOLESALE")));
		parameters.put("salesOnline", box(salesTypes.contains("DISTANCE_SELLING")));
		parameters.put("information", text(permit.path("information")).orElse(""));
		return parameters;
	}

	private static void putDecision(final Map<String, Object> parameters, final JsonNode decision, final Optional<String> caseNumber) {
		put(parameters, "caseNumber", caseNumber);
		put(parameters, DECISION_DATE, text(decision.path(DECISION_DATE)));
		put(parameters, "decisionMaker", text(decision.path("decisionMaker")));
		put(parameters, "decisionText", text(decision.path("decisionText")));
		put(parameters, "replacesDecision", text(decision.path("replacesDecision")));
		put(parameters, "executionDate", text(decision.path("executionDate")));
		put(parameters, "issuedByNameAndTitle", join(", ", text(decision.path(ISSUED_BY).path("name")), text(decision.path(ISSUED_BY).path("title"))));
		put(parameters, "issuedByUnit", text(decision.path(ISSUED_BY).path("unit")));
	}

	private static void putPermitHolder(final Map<String, Object> parameters, final JsonNode holder) {
		put(parameters, "permitHolderName", text(holder.path("name")));
		put(parameters, "permitHolderOrgNumber", text(holder.path("organizationNumber")));
		put(parameters, "permitHolderStreet", text(holder.path(ADDRESS).path("street")));
		put(parameters, "permitHolderPostalAddress", toPostalAddress(holder.path(ADDRESS)));
	}

	private static void putPlace(final Map<String, Object> parameters, final JsonNode place) {
		put(parameters, "premisesName", text(place.path("name")));
		put(parameters, "premisesStreet", text(place.path(ADDRESS).path("street")));
		put(parameters, "premisesPostalAddress", toPostalAddress(place.path(ADDRESS)));
		put(parameters, "premisesPhone", text(place.path("phoneNumber")));
	}

	/**
	 * "05-01 – 09-30 (Slutet sällskap, uteservering)" of an annual period, "2026-06-01 – 2026-08-31" of a fixed one, with
	 * what the period has. A period all year has no dates to show.
	 */
	private static Optional<String> toPeriod(final JsonNode period) {
		final Optional<String> dates;
		if (is(period.path(PERIOD), ANNUAL_PERIOD)) {
			dates = join(RANGE, text(period.path("from")), text(period.path("to")));
		} else if (is(period.path(PERIOD), "FIXED_PERIOD")) {
			dates = join(RANGE, text(period.path("fromDate")), text(period.path("toDate")));
		} else {
			return Optional.empty();
		}
		final var appliesTo = join(", ", text(period.path("audience")).map(AUDIENCES::get), text(period.path("location")).map(LOCATIONS::get),
			text(period.path(DESCRIPTION)));
		return dates.isPresent() ? join(" ", dates, appliesTo.map("(%s)"::formatted)) : appliesTo;
	}

	/** "Allmänheten, inomhus: 11:00–01:00 (Fredag och lördag)", with what the hour has. */
	private static Optional<String> toServingHour(final JsonNode hour) {
		final var appliesTo = join(", ", text(hour.path("audience")).map(AUDIENCES::get), text(hour.path("location")).map(LOCATIONS::get));
		final var time = join("–", text(hour.path("from")), text(hour.path("to")));
		final var description = text(hour.path(DESCRIPTION));
		final var what = time.isPresent() ? join(" ", time, description.map("(%s)"::formatted)) : description;
		return what.map(value -> appliesTo.map(prefix -> "%s: %s".formatted(StringUtils.capitalize(prefix), value)).orElse(value));
	}

	private static Optional<String> toPostalAddress(final JsonNode address) {
		return join(" ", text(address.path("postalCode")), text(address.path("city")));
	}

	@SafeVarargs
	private static Optional<String> join(final String delimiter, final Optional<String>... parts) {
		return joinAll(delimiter, Stream.of(parts));
	}

	private static Optional<String> joinAll(final String delimiter, final Stream<Optional<String>> parts) {
		return Optional.of(parts.flatMap(Optional::stream).collect(joining(delimiter))).filter(StringUtils::isNotBlank);
	}

	private static void put(final Map<String, Object> parameters, final String key, final Optional<String> value) {
		value.ifPresent(present -> parameters.put(key, present));
	}

	private static String box(final boolean checked) {
		return String.valueOf(checked);
	}

	private static boolean is(final JsonNode node, final String value) {
		return text(node).map(value::equals).orElse(false);
	}

	private static Optional<String> text(final JsonNode node) {
		return Optional.of(node)
			.filter(JsonNode::isValueNode)
			.filter(value -> !value.isNull())
			.map(JsonNode::asString)
			.map(String::strip)
			.filter(StringUtils::isNotBlank);
	}

	private static List<String> strings(final JsonNode array) {
		return array.values().stream()
			.map(PermitCertificateMapper::text)
			.flatMap(Optional::stream)
			.toList();
	}
}
