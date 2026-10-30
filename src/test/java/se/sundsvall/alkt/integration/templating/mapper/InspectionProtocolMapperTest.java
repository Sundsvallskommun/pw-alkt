package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.alkt.exception.NonRetryableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionProtocolMapperTest {

	private static final String INTERNAL_TEMPLATE = "internal-inspection-protocol";
	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-10-01T09:00:00+02:00");

	private static Measure checkpoint(final String title, final String result, final String description) {
		return new Measure().type("INTERNAL").status("ACTIVE").title(title).result(result).description(description).created(CREATED);
	}

	@Test
	void sendsTheTextFieldsOfTheInvestigationUnderTheirOwnKeys() {
		final var investigation = new Investigation().parameters(List.of(
			new Parameter().key("businessName").values(List.of("Runt Hörnet AB")),
			new Parameter().key("inspectionDate").values(List.of("2026-10-15"))));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).containsEntry("businessName", "Runt Hörnet AB").containsEntry("inspectionDate", "2026-10-15");
	}

	@Test
	void leavesOutATextFieldWithoutValue() {
		final var investigation = new Investigation().parameters(List.of(
			new Parameter().key("businessName").values(List.of()),
			new Parameter().key("premisesName").values(List.of(" ", "")),
			new Parameter().key("inspector")));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).doesNotContainKeys("businessName", "premisesName", "inspector").containsOnlyKeys("measures");
	}

	@Test
	void listsTheCheckpointsInTheOrderTheyWereCreated() {
		final var first = checkpoint("Serveringsansvarig", "COMPLETED", null).created(CREATED);
		final var second = checkpoint("Kassaregister", "COMPLETED", null).created(CREATED.plusMinutes(1));
		final var third = checkpoint("Alkoholpolicy", "COMPLETED", null).created(CREATED.plusMinutes(2));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(), List.of(third, first, second));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Serveringsansvarig", "noRemark", "X", "remark", "", "comment", ""),
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", ""),
			Map.of("title", "Alkoholpolicy", "noRemark", "X", "remark", "", "comment", "")));
	}

	@Test
	void marksACompletedCheckpointWithoutRemark() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(),
			List.of(checkpoint("Kassaregister", "COMPLETED", "Kassaregistret är certifierat")));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", "Kassaregistret är certifierat")));
	}

	@Test
	void marksANotCompletedCheckpointWithRemark() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(),
			List.of(checkpoint("Alkoholpolicy", "NOT_COMPLETED", "Policyn saknas")));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Alkoholpolicy", "noRemark", "", "remark", "X", "comment", "Policyn saknas")));
	}

	@Test
	void commentsANotApplicableCheckpointWithoutDescription() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(),
			List.of(checkpoint("Matservering", "NOT_APPLICABLE", null), checkpoint("Uteservering", "NOT_APPLICABLE", " ").created(CREATED.plusMinutes(1))));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Matservering", "noRemark", "", "remark", "", "comment", "Ej aktuellt"),
			Map.of("title", "Uteservering", "noRemark", "", "remark", "", "comment", "Ej aktuellt")));
	}

	@Test
	void keepsTheDescriptionOfANotApplicableCheckpoint() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(),
			List.of(checkpoint("Matservering", "NOT_APPLICABLE", "Ingen matservering på kvällen")));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Matservering", "noRemark", "", "remark", "", "comment", "Ingen matservering på kvällen")));
	}

	@Test
	void leavesBothMarksEmptyForACheckpointNotAssessed() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(),
			List.of(checkpoint("Kassaregister", null, "Kontrolleras vid nästa besök"), checkpoint("Alkoholpolicy", null, null).created(CREATED.plusMinutes(1))));

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "", "remark", "", "comment", "Kontrolleras vid nästa besök"),
			Map.of("title", "Alkoholpolicy", "noRemark", "", "remark", "", "comment", "")));
	}

	@Test
	void leavesOutCheckpointsThatAreNotActiveAndMeasuresOfOtherTypes() {
		final var measures = List.of(
			checkpoint("Kassaregister", "COMPLETED", null),
			checkpoint("Avbruten", "COMPLETED", null).status("CANCELLED"),
			checkpoint("Borttagen", "COMPLETED", null).status("DELETED"),
			checkpoint("Utan status", "COMPLETED", null).status(null),
			checkpoint("Åtgärdspunkt", "NOT_COMPLETED", null).type("EXTERNAL"),
			checkpoint("Uteservering", "NOT_COMPLETED", null).type("OBSERVATION"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, new Investigation(), measures);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", "")));
	}

	@Test
	void ticksTheBoxOfTheChosenSummary() {
		final var investigation = new Investigation().parameters(List.of(new Parameter().key("summary").values(List.of("actionRequired"))));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).containsEntry("summaryApprovedActionRequired", "true")
			.doesNotContainKeys("summary", "summaryApproved", "summaryNotApprovedRevisit", "summaryInvestigationOpened");
	}

	@ParameterizedTest
	@CsvSource({
		"visit, premises, visitInPremises",
		"visit, closed, visitClosed",
		"coordinatedWith, none, coordinatedNone",
		"coordinatedWith, police, coordinatedPolice",
		"coordinatedWith, taxAgency, coordinatedTaxAgency",
		"coordinatedWith, rescueService, coordinatedRescueService",
		"coordinatedWith, environmentHealth, coordinatedEnvironmentHealth",
		"coordinatedWith, other, coordinatedOther",
		"securityGuard, yes, securityGuardYes",
		"securityGuard, no, securityGuardNo",
		"entranceHost, yes, entranceHostYes",
		"entranceHost, no, entranceHostNo",
		"responsiblePerson, present, responsiblePersonPresent",
		"responsiblePerson, absent, responsiblePersonAbsent",
		"summary, approved, summaryApproved",
		"summary, actionRequired, summaryApprovedActionRequired",
		"summary, revisit, summaryNotApprovedRevisit",
		"summary, investigation, summaryInvestigationOpened"
	})
	void ticksTheBoxOfEachChoice(final String key, final String value, final String box) {
		final var investigation = new Investigation().parameters(List.of(new Parameter().key(key).values(List.of(value))));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).containsOnlyKeys(box, "measures").containsEntry(box, "true");
	}

	@Test
	void ticksEveryChosenCoordination() {
		final var investigation = new Investigation().parameters(List.of(new Parameter().key("coordinatedWith").values(List.of("taxAgency", "police"))));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).containsOnlyKeys("coordinatedTaxAgency", "coordinatedPolice", "measures")
			.containsEntry("coordinatedTaxAgency", "true")
			.containsEntry("coordinatedPolice", "true");
	}

	@Test
	void ticksNoBoxForAChoiceNotMade() {
		final var investigation = new Investigation().parameters(List.of(
			new Parameter().key("visit").values(List.of()),
			new Parameter().key("coordinatedWith").values(List.of(" ")),
			new Parameter().key("summary"),
			new Parameter().key("businessName").values(List.of("Runt Hörnet AB"))));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, List.of(checkpoint("Tillstånd", "COMPLETED", null)));

		assertThat(parameters).containsOnlyKeys("businessName", "measures");
	}

	@Test
	void failsWithoutRetryOnAnUnknownChoice() {
		final var investigation = new Investigation().parameters(List.of(new Parameter().key("summary").values(List.of("maybe"))));
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, measures))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("summary")
			.hasMessageContaining("maybe");
	}

	@Test
	void failsWithoutRetryOnAnUnknownChoiceAmongKnownOnes() {
		final var investigation = new Investigation().parameters(List.of(new Parameter().key("coordinatedWith").values(List.of("police", "customs"))));
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(INTERNAL_TEMPLATE, investigation, measures))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("coordinatedWith")
			.hasMessageContaining("customs");
	}
}
