package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.alkt.exception.NonRetryableException;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;

class TemplatingMapperTest {

	@Test
	void toTemplateParametersNamesEachParameterByItsKey() {
		final var decision = new Decision().parameters(List.of(parameter("caseNumber", "IAN-2026-00209"), parameter("permitHolderName", "Runt Hörnet AB")));

		assertThat(toTemplateParameters(decision)).containsExactly(
			entry("caseNumber", "IAN-2026-00209"),
			entry("permitHolderName", "Runt Hörnet AB"),
			entry(PARAMETER_CONDITIONS, ""));
	}

	@Test
	void toTemplateParametersJoinsTheValuesOfAParameterLeavingOutBlankOnes() {
		final var decision = new Decision().parameters(List.of(parameter("serveringsyta", "Matsalen", " ", "Uteserveringen")));

		assertThat(toTemplateParameters(decision)).containsEntry("serveringsyta", "Matsalen, Uteserveringen");
	}

	/** A missing key makes the strict template answer 400 naming it, where an empty one would render an empty field. */
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "  ")
	void toTemplateParametersLeavesOutAParameterWithoutValue(final String value) {
		final var decision = new Decision().parameters(List.of(new Parameter().key("premisesName").values(singletonList(value)), new Parameter().key("premisesPhone")));

		assertThat(toTemplateParameters(decision)).containsOnlyKeys(PARAMETER_CONDITIONS);
	}

	@Test
	void toTemplateParametersPutsTheTermsAsConditionsOnePerLineInTheirOrder() {
		final var decision = new Decision().terms(List.of(
			new DecisionTerm().sortOrder(2).text("Godkänd matsal ska finnas."),
			new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat."),
			new DecisionTerm().sortOrder(3).text(" ")));

		assertThat(toTemplateParameters(decision)).containsEntry(PARAMETER_CONDITIONS, "Serveringsområdet ska vara avgränsat.\nGodkänd matsal ska finnas.");
	}

	@Test
	void toTemplateParametersPutsTheParametersFirstAndATermWithoutSortOrderLast() {
		final var decision = new Decision()
			.parameters(List.of(parameter("caseNumber", "IAN-2026-00209"), parameter("permitHolderName", "Runt Hörnet AB")))
			.terms(List.of(
				new DecisionTerm().text("Ordningsvakt ska finnas efter 23.00."),
				new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat.")));

		assertThat(toTemplateParameters(decision)).containsExactly(
			entry("caseNumber", "IAN-2026-00209"),
			entry("permitHolderName", "Runt Hörnet AB"),
			entry(PARAMETER_CONDITIONS, "Serveringsområdet ska vara avgränsat.\nOrdningsvakt ska finnas efter 23.00."));
	}

	@Test
	void toTemplateParametersLetsTheTermsWinOverAParameterNamedConditions() {
		final var decision = new Decision()
			.parameters(List.of(parameter(PARAMETER_CONDITIONS, "Från en parameter.")))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat.")));

		assertThat(toTemplateParameters(decision)).containsExactly(entry(PARAMETER_CONDITIONS, "Serveringsområdet ska vara avgränsat."));
	}

	@Test
	void toTemplateParametersHasEmptyConditionsForAnEmptyDecision() {
		assertThat(toTemplateParameters(new Decision())).containsExactly(entry(PARAMETER_CONDITIONS, ""));
	}

	@Test
	void toTemplateParametersHasEmptyConditionsForAnApprovalWithoutTerms() {
		assertThat(toTemplateParameters(new Decision().outcome(DECISION_OUTCOME_APPROVAL))).containsExactly(entry(PARAMETER_CONDITIONS, ""));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "  ")
	void toTemplateParametersRefusesAnApprovalWithConditionsWithoutConditions(final String text) {
		final var decision = new Decision().id("decision-id").outcome(DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS).terms(List.of(new DecisionTerm().sortOrder(1).text(text)));

		assertThatThrownBy(() -> toTemplateParameters(decision))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision decision-id is an approval with conditions but has no conditions, so no certificate is made");
	}

	@Test
	void toTemplateParametersRefusesAnApprovalWithConditionsWithoutTerms() {
		final var decision = new Decision().outcome(DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS);

		assertThatThrownBy(() -> toTemplateParameters(decision)).isInstanceOf(NonRetryableException.class);
	}

	@Test
	void toTemplateParametersOfAChangeLetsThePermitWinOverTheDecision() {
		final var decision = new Decision().parameters(List.of(parameter("serveringstid", "11.00–01.00"), parameter("caseNumber", "IAN-2026-00209")));

		assertThat(toTemplateParameters(decision, Map.of("serveringstid", "11.00–02.00", "permitHolderName", "Runt Hörnet AB"))).containsOnly(
			entry("serveringstid", "11.00–02.00"),
			entry("caseNumber", "IAN-2026-00209"),
			entry("permitHolderName", "Runt Hörnet AB"),
			entry(PARAMETER_CONDITIONS, ""));
	}

	@Test
	void toTemplateParametersOfAChangeTakesTheConditionsOfThePermitForAnApprovalWithConditions() {
		final var decision = new Decision().outcome(DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS);

		assertThat(toTemplateParameters(decision, Map.of(PARAMETER_CONDITIONS, "Ordningsvakt efter 23.00.")))
			.containsExactly(entry(PARAMETER_CONDITIONS, "Ordningsvakt efter 23.00."));
	}

	@Test
	void toTemplateParametersOfAChangeRefusesAnApprovalWithConditionsWhenThePermitHasNoneEither() {
		final var decision = new Decision().id("decision-id").outcome(DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS);

		assertThatThrownBy(() -> toTemplateParameters(decision, Map.of("serveringstid", "11.00–02.00")))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision decision-id is an approval with conditions but has no conditions, so no certificate is made");
	}

	private static Parameter parameter(final String key, final String... values) {
		return new Parameter().key(key).values(List.of(values));
	}
}
