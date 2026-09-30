package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.DecisionTerm;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class TemplatingMapper {

	private TemplatingMapper() {}

	/**
	 * Each term becomes a placeholder named by its category. The last term of a category wins. A term without text is left
	 * out, so the strict template answers 400 naming it instead of rendering an empty field.
	 */
	public static Map<String, Object> toTemplateParameters(final List<DecisionTerm> terms) {
		final var parameters = new LinkedHashMap<String, Object>();
		Optional.ofNullable(terms).orElse(emptyList()).stream()
			.filter(term -> term.getCategory() != null && isNotBlank(term.getText()))
			.forEach(term -> parameters.put(term.getCategory(), term.getText()));
		return parameters;
	}
}
