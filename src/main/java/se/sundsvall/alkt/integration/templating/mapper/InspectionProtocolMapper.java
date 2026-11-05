package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Measure;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.util.JsonText;
import tools.jackson.databind.JsonNode;

import static java.util.Comparator.nullsLast;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toActiveMeasures;

public final class InspectionProtocolMapper {

	private static final String MEASURE_TYPE_INTERNAL = "INTERNAL";
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

	/**
	 * The placeholders of the protocol: each answer under its key, a ticked box for each choice made, the checkpoints and,
	 * in the serving protocol, the order. Fails without retry when the answers are not a JSON object, a choice is not one
	 * of the protocol, or there are no active checkpoints.
	 */
	public static Map<String, Object> toTemplateParameters(final String errandId, final String template, final JsonNode answers, final List<Measure> measures,
		final String caseNumber) {
		final var checkpoints = toActiveMeasures(measures, MEASURE_TYPE_INTERNAL);
		if (checkpoints.isEmpty()) {
			throw new NonRetryableException("Errand '%s' has no active checkpoints, so no protocol is made".formatted(errandId));
		}
		if (!answers.isObject()) {
			throw new NonRetryableException("The inspection protocol of errand '%s' must be a JSON object, but was %s".formatted(errandId, answers.getNodeType()));
		}

		final var parameters = new LinkedHashMap<String, Object>();
		answers.properties().forEach(answer -> {
			final var key = answer.getKey();
			final var values = textsOf(answer.getValue());
			if (CHOICES.containsKey(key)) {
				values.forEach(value -> parameters.put(toBox(errandId, key, value), BOX_TICKED));
			} else if (!values.isEmpty()) {
				parameters.put(key, String.join(", ", values));
			}
		});
		parameters.put("measures", checkpoints.stream()
			.sorted(Comparator.comparing(Measure::getCreated, nullsLast(Comparator.naturalOrder())))
			.map(InspectionProtocolMapper::toRow)
			.toList());
		if (SERVING_TEMPLATE.equals(template)) {
			parameters.putAll(toOrder(errandId, toActiveMeasures(measures, MEASURE_TYPE_OBSERVATION)));
		}
		parameters.put("caseNumber", caseNumber);
		return parameters;
	}

	private static Map<String, String> toOrder(final String errandId, final List<Measure> places) {
		final var order = new LinkedHashMap<String, String>();
		var remarks = false;
		for (final var place : places) {
			final var suffix = Optional.ofNullable(place.getTitle()).map(PLACES::get)
				.orElseThrow(() -> new NonRetryableException("Errand '%s' has an observation of the place '%s', which is not among the places of the inspection protocol in pw-alkt".formatted(errandId, place.getTitle())));
			if (!RESULT_NOT_COMPLETED.equals(place.getResult())) {
				continue;
			}
			remarks = true;
			for (final var thing : Optional.ofNullable(place.getResultText()).orElse("").split("\\|")) {
				if (thing.isBlank()) {
					continue;
				}
				if (!OBSERVED_THINGS.contains(thing)) {
					throw new NonRetryableException("Errand '%s' has observed '%s' at the place '%s', which is not among the observations of the inspection protocol in pw-alkt".formatted(errandId, thing, place.getTitle()));
				}
				order.put("observation" + thing + suffix, MARK);
			}
		}
		order.put(remarks ? "orderWithRemarks" : "orderWithoutRemarks", BOX_TICKED);
		return order;
	}

	/** The texts of an answer, which is one text or, for a question with several choices, a list of them. */
	private static List<String> textsOf(final JsonNode answer) {
		return (answer.isArray() ? answer.values().stream() : Stream.of(answer))
			.map(JsonText::text)
			.flatMap(Optional::stream)
			.toList();
	}

	private static String toBox(final String errandId, final String key, final String value) {
		return Optional.ofNullable(CHOICES.get(key).get(value))
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has %s '%s' in its investigation, which is not among the choices of the inspection protocol in pw-alkt".formatted(errandId, key, value)));
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
