package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

import static java.util.Collections.emptyList;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class TemplatingMapper {

	static final String PARAMETER_CONDITIONS = "conditions";

	private TemplatingMapper() {}

	/**
	 * Each parameter of the decision becomes a placeholder named by its key, its values joined. A parameter without a value
	 * is left out, so the strict template answers 400 naming it instead of rendering an empty field. The terms are the
	 * conditions of the decision, one per line, and empty for a decision without conditions.
	 */
	public static Map<String, Object> toTemplateParameters(final Decision decision) {
		final var templateParameters = new LinkedHashMap<String, Object>();
		Optional.ofNullable(decision.getParameters()).orElse(emptyList())
			.forEach(parameter -> {
				final var value = toValue(parameter);
				if (isNotBlank(value)) {
					templateParameters.put(parameter.getKey(), value);
				}
			});
		templateParameters.put(PARAMETER_CONDITIONS, toConditions(decision));
		return templateParameters;
	}

	private static String toValue(final Parameter parameter) {
		return Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream()
			.filter(StringUtils::isNotBlank)
			.collect(joining(", "));
	}

	private static String toConditions(final Decision decision) {
		return Optional.ofNullable(decision.getTerms()).orElse(emptyList()).stream()
			.sorted(Comparator.comparing(DecisionTerm::getSortOrder, nullsLast(Comparator.naturalOrder())))
			.map(DecisionTerm::getText)
			.filter(StringUtils::isNotBlank)
			.collect(joining("\n"));
	}
}
