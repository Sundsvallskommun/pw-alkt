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

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;

public final class InspectionProtocolMapper {

	private static final String MEASURE_TYPE_INTERNAL = "INTERNAL";
	private static final String MEASURE_STATUS_ACTIVE = "ACTIVE";
	private static final String RESULT_COMPLETED = "COMPLETED";
	private static final String RESULT_NOT_COMPLETED = "NOT_COMPLETED";
	private static final String RESULT_NOT_APPLICABLE = "NOT_APPLICABLE";
	private static final String MARK = "X";

	private InspectionProtocolMapper() {}

	public static Map<String, Object> toTemplateParameters(final String template, final Investigation investigation, final List<Measure> measures) {
		final var parameters = new LinkedHashMap<String, Object>(toParameterValues(investigation.getParameters()));
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
