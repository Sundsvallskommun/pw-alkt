package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;

public final class InspectionProtocolMapper {

	private static final String MEASURE_TYPE_INTERNAL = "INTERNAL";
	private static final String MEASURE_STATUS_ACTIVE = "ACTIVE";
	private static final String RESULT_COMPLETED = "COMPLETED";
	private static final String RESULT_NOT_COMPLETED = "NOT_COMPLETED";
	private static final String RESULT_NOT_APPLICABLE = "NOT_APPLICABLE";
	private static final String MARK = "X";
	private static final String BOX_TICKED = "true";
	private static final String SERVING_TEMPLATE = "serving-inspection-protocol";
	private static final String MEASURE_TYPE_OBSERVATION = "OBSERVATION";
	private static final Map<String, String> PLACES = Map.of(
		"Serveringsyta inomhus", "Indoor",
		"Uteservering", "Outdoor",
		"Utanför (entré)", "Entrance",
		"Toaletter", "Toilets");
	private static final Set<String> OBSERVED_THINGS = Set.of("GlassBreakage", "CrowdedTables", "Littering", "Vomit", "StickyFloor", "Disorder", "TooManyGuests",
		"DancingOnFurniture", "InadequateBoundary", "MissingSeating", "AlcoholOutsideServingArea", "Smoking", "HighNoiseLevel");
	private static final Map<String, Map<String, String>> CHOICES = Map.of(
		"visit", Map.of("premises", "visitInPremises", "closed", "visitClosed"),
		"coordinatedWith", Map.of("none", "coordinatedNone", "police", "coordinatedPolice", "taxAgency", "coordinatedTaxAgency", "rescueService", "coordinatedRescueService",
			"environmentHealth", "coordinatedEnvironmentHealth", "other", "coordinatedOther"),
		"securityGuard", Map.of("yes", "securityGuardYes", "no", "securityGuardNo"),
		"entranceHost", Map.of("yes", "entranceHostYes", "no", "entranceHostNo"),
		"responsiblePerson", Map.of("present", "responsiblePersonPresent", "absent", "responsiblePersonAbsent"),
		"summary", Map.of("approved", "summaryApproved", "actionRequired", "summaryApprovedActionRequired", "revisit", "summaryNotApprovedRevisit", "investigation",
			"summaryInvestigationOpened"));

	private InspectionProtocolMapper() {}

	public static Map<String, Object> toTemplateParameters(final String template, final Investigation investigation, final List<Measure> measures) {
		final var parameters = new LinkedHashMap<String, Object>(toParameterValues(investigation.getParameters()));
		CHOICES.keySet().forEach(parameters::remove);
		Optional.ofNullable(investigation.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> CHOICES.containsKey(parameter.getKey()))
			.forEach(parameter -> Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream()
				.filter(StringUtils::isNotBlank)
				.forEach(value -> parameters.put(toBox(parameter.getKey(), value), BOX_TICKED)));
		parameters.put("measures", toActiveCheckpoints(measures).stream()
			.sorted(Comparator.comparing(Measure::getCreated, Comparator.nullsLast(Comparator.<OffsetDateTime>naturalOrder())))
			.map(InspectionProtocolMapper::toRow)
			.toList());
		if (SERVING_TEMPLATE.equals(template)) {
			parameters.putAll(toOrder(activeOfType(measures, MEASURE_TYPE_OBSERVATION)));
		}
		return parameters;
	}

	private static Map<String, String> toOrder(final List<Measure> places) {
		final var order = new LinkedHashMap<String, String>();
		var remarks = false;
		for (final var place : places) {
			final var suffix = Optional.ofNullable(place.getTitle()).map(PLACES::get)
				.orElseThrow(() -> new NonRetryableException("The errand has an observation of the place '%s', which is not a place the protocol knows".formatted(place.getTitle())));
			if (!RESULT_NOT_COMPLETED.equals(place.getResult())) {
				continue;
			}
			remarks = true;
			for (final var thing : Optional.ofNullable(place.getResultText()).orElse("").split("\\|")) {
				if (thing.isBlank()) {
					continue;
				}
				if (!OBSERVED_THINGS.contains(thing)) {
					throw new NonRetryableException("The errand has observed '%s' at the place '%s', which is not an observation the protocol knows".formatted(thing, place.getTitle()));
				}
				order.put("observation" + thing + suffix, MARK);
			}
		}
		order.put(remarks ? "orderWithRemarks" : "orderWithoutRemarks", BOX_TICKED);
		return order;
	}

	public static List<Measure> toActiveCheckpoints(final List<Measure> measures) {
		return activeOfType(measures, MEASURE_TYPE_INTERNAL);
	}

	private static List<Measure> activeOfType(final List<Measure> measures, final String type) {
		return Optional.ofNullable(measures).orElse(emptyList()).stream()
			.filter(measure -> type.equals(measure.getType()))
			.filter(measure -> MEASURE_STATUS_ACTIVE.equals(measure.getStatus()))
			.toList();
	}

	private static String toBox(final String key, final String value) {
		return Optional.ofNullable(CHOICES.get(key).get(value))
			.orElseThrow(() -> new NonRetryableException("The investigation has %s '%s', which is not a choice the protocol knows".formatted(key, value)));
	}

	private static Map<String, String> toRow(final Measure measure) {
		final var comment = Optional.ofNullable(measure.getDescription()).filter(StringUtils::isNotBlank)
			.orElse(RESULT_NOT_APPLICABLE.equals(measure.getResult()) ? "Ej aktuellt" : "");
		return Map.of(
			"title", Optional.ofNullable(measure.getTitle()).orElse(""),
			"noRemark", RESULT_COMPLETED.equals(measure.getResult()) ? MARK : "",
			"remark", RESULT_NOT_COMPLETED.equals(measure.getResult()) ? MARK : "",
			"comment", comment);
	}
}
