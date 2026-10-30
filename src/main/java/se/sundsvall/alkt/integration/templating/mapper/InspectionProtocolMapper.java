package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
		parameters.put("measures", activeOfType(measures, MEASURE_TYPE_INTERNAL).stream()
			.sorted(Comparator.comparing(Measure::getCreated, Comparator.nullsLast(Comparator.<OffsetDateTime>naturalOrder())))
			.map(InspectionProtocolMapper::toRow)
			.toList());
		return parameters;
	}

	public static List<Measure> activeOfType(final List<Measure> measures, final String type) {
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
