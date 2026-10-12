package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Investigation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import se.sundsvall.alkt.exception.NonRetryableException;

import static java.util.Collections.emptyMap;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toConditions;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;

public final class TemplatingMapper {

	private TemplatingMapper() {}

	/**
	 * Each parameter of the decision becomes a placeholder named by its key, its values joined. A parameter without a value
	 * is left out, so the strict template answers 400 naming it instead of rendering an empty field. The terms are the
	 * conditions of the decision, one per line, and empty for a decision without conditions.
	 */
	public static Map<String, Object> toTemplateParameters(final Decision decision) {
		return toTemplateParameters(decision, emptyMap());
	}

	/**
	 * For a change: the parameters of the permit, the change already merged in, win over those of the decision, so the
	 * conditions the permit keeps count as conditions of the decision.
	 */
	public static Map<String, Object> toTemplateParameters(final Decision decision, final Map<String, String> permitParameters) {
		final var conditions = Optional.ofNullable(permitParameters.get(PERMIT_PARAMETER_CONDITIONS)).orElseGet(() -> toConditions(decision));
		// Why: an empty conditions field is valid for a plain approval, so the template would render the permit without them.
		if (DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS.equals(decision.getOutcome()) && conditions.isBlank()) {
			throw new NonRetryableException("Decision %s is an approval with conditions but has no conditions, so no certificate is made".formatted(decision.getId()));
		}

		final var templateParameters = new LinkedHashMap<String, Object>(toParameterValues(decision.getParameters()));
		// Why: set by the process, so only the number the permit carries is rendered.
		templateParameters.remove(PERMIT_PARAMETER_RESTAURANT_NUMBER);
		templateParameters.putAll(permitParameters);
		templateParameters.put(PERMIT_PARAMETER_CONDITIONS, conditions);
		return templateParameters;
	}

	/** The same placeholders as for a decision, without conditions since an investigation has none. */
	public static Map<String, Object> toTemplateParameters(final Investigation investigation) {
		return new LinkedHashMap<>(toParameterValues(investigation.getParameters()));
	}
}
