package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.DecisionTerm;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;

class TemplatingMapperTest {

	@Test
	void toTemplateParametersNamesEachTermByItsCategory() {
		final var terms = List.of(term("caseNumber", "IAN-2026-00209"), term("permitHolderName", "Runt Hörnet AB"));

		assertThat(toTemplateParameters(terms)).containsExactly(
			entry("caseNumber", "IAN-2026-00209"),
			entry("permitHolderName", "Runt Hörnet AB"));
	}

	/** A missing key makes the strict template answer 400 naming it, where an empty one would render an empty field. */
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "  ")
	void toTemplateParametersLeavesOutATermWithoutText(final String text) {
		assertThat(toTemplateParameters(List.of(term("premisesName", text), term("caseNumber", "IAN-2026-00209")))).containsExactly(entry("caseNumber", "IAN-2026-00209"));
	}

	@Test
	void toTemplateParametersLeavesOutATermWithoutCategory() {
		assertThat(toTemplateParameters(List.of(term(null, "text"), term("caseNumber", "IAN-2026-00209")))).containsExactly(entry("caseNumber", "IAN-2026-00209"));
	}

	@Test
	void toTemplateParametersKeepsTheLastTermOfACategory() {
		assertThat(toTemplateParameters(List.of(term("caseNumber", "first"), term("caseNumber", "second")))).containsExactly(entry("caseNumber", "second"));
	}

	@Test
	void toTemplateParametersIsEmptyWithoutTerms() {
		assertThat(toTemplateParameters(null)).isEmpty();
	}

	private static DecisionTerm term(final String category, final String text) {
		return new DecisionTerm().category(category).text(text);
	}
}
