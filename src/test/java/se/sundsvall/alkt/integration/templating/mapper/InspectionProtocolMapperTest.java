package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Measure;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.alkt.exception.NonRetryableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionProtocolMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final JsonNode NO_ANSWERS = json("{}");
	private static final String ERRAND_ID = "errand-id";
	private static final String CASE_NUMBER = "AOT-26100148";
	private static final String INTERNAL_TEMPLATE = "internal-inspection-protocol";
	private static final String SERVING_TEMPLATE = "serving-inspection-protocol";
	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-10-01T09:00:00+02:00");

	private static Measure checkpoint(final String title, final String result, final String description) {
		return new Measure().type("INTERNAL").status("ACTIVE").title(title).result(result).description(description).created(CREATED);
	}

	@Test
	void sendsTheTextAnswersUnderTheirOwnKeys() {
		final var answers = json("""
			{ "businessName": "Runt Hörnet AB", "inspectionDate": "2026-10-15", "guestCount": 40, "inspectors": ["Kim Karlsson", "Alex Andersson"] }
			""");

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters)
			.containsEntry("businessName", "Runt Hörnet AB")
			.containsEntry("inspectionDate", "2026-10-15")
			.containsEntry("guestCount", "40")
			.containsEntry("inspectors", "Kim Karlsson, Alex Andersson");
	}

	@Test
	void sendsNoAnswerThatIsBlankNullOrAnObject() {
		final var answers = json("""
			{ "businessName": " ", "inspectionDate": null, "business": { "name": "Runt Hörnet AB" }, "inspectors": [] }
			""");

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures");
	}

	@Test
	void failsWithoutRetryWhenTheAnswersAreNotAJsonObject() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null));
		final var answers = json("[]");

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, measures, CASE_NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("The inspection protocol of errand 'errand-id' must be a JSON object, but was ARRAY");
	}

	@Test
	void listsTheCheckpointsInTheOrderTheyWereCreated() {
		final var first = checkpoint("Serveringsansvarig", "COMPLETED", null).created(CREATED);
		final var second = checkpoint("Kassaregister", "COMPLETED", null).created(CREATED.plusMinutes(1));
		final var third = checkpoint("Alkoholpolicy", "COMPLETED", null).created(CREATED.plusMinutes(2));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS, List.of(third, first, second), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Serveringsansvarig", "noRemark", "X", "remark", "", "comment", ""),
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", ""),
			Map.of("title", "Alkoholpolicy", "noRemark", "X", "remark", "", "comment", "")));
	}

	@Test
	void marksANotCompletedCheckpointWithRemark() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS,
			List.of(checkpoint("Alkoholpolicy", "NOT_COMPLETED", "Policyn saknas")), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Alkoholpolicy", "noRemark", "", "remark", "X", "comment", "Policyn saknas")));
	}

	@Test
	void commentsANotApplicableCheckpointWithoutDescription() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS,
			List.of(checkpoint("Matservering", "NOT_APPLICABLE", null), checkpoint("Uteservering", "NOT_APPLICABLE", " ").created(CREATED.plusMinutes(1))), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Matservering", "noRemark", "", "remark", "", "comment", "Ej aktuellt"),
			Map.of("title", "Uteservering", "noRemark", "", "remark", "", "comment", "Ej aktuellt")));
	}

	@Test
	void keepsTheDescriptionOfANotApplicableCheckpoint() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS,
			List.of(checkpoint("Matservering", "NOT_APPLICABLE", "Ingen matservering på kvällen")), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Matservering", "noRemark", "", "remark", "", "comment", "Ingen matservering på kvällen")));
	}

	@Test
	void leavesBothMarksEmptyForACheckpointNotAssessed() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS,
			List.of(checkpoint("Kassaregister", null, "Kontrolleras vid nästa besök"), checkpoint("Alkoholpolicy", null, null).created(CREATED.plusMinutes(1))), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "", "remark", "", "comment", "Kontrolleras vid nästa besök"),
			Map.of("title", "Alkoholpolicy", "noRemark", "", "remark", "", "comment", "")));
	}

	@Test
	void leavesOutCheckpointsThatAreNotActiveAndMeasuresOfOtherTypes() {
		final var measures = List.of(
			new Measure().type("INTERNAL").status("ACTIVE").title("Kassaregister").result("COMPLETED").created(CREATED),
			new Measure().type("INTERNAL").status("CANCELLED").title("Alkoholpolicy").result("COMPLETED").created(CREATED),
			new Measure().type("INTERNAL").status("DELETED").title("Matservering").result("COMPLETED").created(CREATED),
			new Measure().type("INTERNAL").title("Ordningsvakter").result("COMPLETED").created(CREATED),
			new Measure().type("EXTERNAL").status("ACTIVE").title("Matsedel saknas").result("NOT_COMPLETED").created(CREATED),
			new Measure().type("OBSERVATION").status("ACTIVE").title("Uteservering").result("NOT_COMPLETED").created(CREATED));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", "")));
	}

	@Test
	void listsACheckpointWithoutCreationLast() {
		final var withoutCreation = new Measure().type("INTERNAL").status("ACTIVE").title("Ordningsvakter").result("COMPLETED");
		final var created = new Measure().type("INTERNAL").status("ACTIVE").title("Kassaregister").result("COMPLETED").created(CREATED);

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS, List.of(withoutCreation, created), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "Kassaregister", "noRemark", "X", "remark", "", "comment", ""),
			Map.of("title", "Ordningsvakter", "noRemark", "X", "remark", "", "comment", "")));
	}

	@Test
	void leavesTheTitleEmptyForACheckpointWithoutTitle() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS,
			List.of(new Measure().type("INTERNAL").status("ACTIVE").result("NOT_COMPLETED").description("Policyn saknas").created(CREATED)), CASE_NUMBER);

		assertThat(parameters.get("measures")).isEqualTo(List.of(
			Map.of("title", "", "noRemark", "", "remark", "X", "comment", "Policyn saknas")));
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
		final var answers = json("""
			{ "%s": "%s" }
			""".formatted(key, value));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", box, "measures").containsEntry(box, "true");
	}

	@Test
	void ticksEveryChosenCoordination() {
		final var answers = json("""
			{ "coordinatedWith": ["taxAgency", "police"] }
			""");

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "coordinatedTaxAgency", "coordinatedPolice", "measures")
			.containsEntry("coordinatedTaxAgency", "true")
			.containsEntry("coordinatedPolice", "true");
	}

	@Test
	void ticksNoBoxForAChoiceNotMade() {
		final var answers = json("""
			{ "visit": [], "coordinatedWith": [" "], "summary": null, "securityGuard": "", "businessName": "Runt Hörnet AB" }
			""");

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "businessName", "measures");
	}

	@Test
	void failsWithoutRetryOnAnUnknownChoiceAmongKnownOnes() {
		final var answers = json("""
			{ "coordinatedWith": ["police", "customs"] }
			""");
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, answers, measures, CASE_NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has coordinatedWith 'customs' in its investigation, which is not among the choices of the inspection protocol in pw-alkt");
	}

	private static Measure place(final String label, final String result, final String observed) {
		return new Measure().type("OBSERVATION").status("ACTIVE").title(label).result(result).resultText(observed).created(CREATED);
	}

	@Test
	void ticksTheObservedThingsOfAPlaceWithDeficiencies() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "NOT_COMPLETED", "CrowdedTables|Littering"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "observationCrowdedTablesOutdoor", "observationLitteringOutdoor", "orderWithRemarks")
			.containsEntry("observationCrowdedTablesOutdoor", "X")
			.containsEntry("observationLitteringOutdoor", "X")
			.containsEntry("orderWithRemarks", "true");
	}

	@ParameterizedTest
	@CsvSource({
		"Serveringsyta inomhus, observationGlassBreakageIndoor",
		"Uteservering, observationGlassBreakageOutdoor",
		"Utanför (entré), observationGlassBreakageEntrance",
		"Toaletter, observationGlassBreakageToilets"
	})
	void namesTheCellAfterThePlace(final String label, final String cell) {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place(label, "NOT_COMPLETED", "GlassBreakage"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", cell, "orderWithRemarks").containsEntry(cell, "X");
	}

	@ParameterizedTest
	@CsvSource({
		"GlassBreakage", "CrowdedTables", "Littering", "Vomit", "StickyFloor", "Disorder", "TooManyGuests", "DancingOnFurniture", "InadequateBoundary", "MissingSeating",
		"AlcoholOutsideServingArea", "Smoking", "HighNoiseLevel"
	})
	void knowsEveryObservedThing(final String thing) {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Toaletter", "NOT_COMPLETED", thing));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsEntry("observation" + thing + "Toilets", "X");
	}

	@Test
	void ticksNothingForAPlaceWithoutDeficiencies() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "COMPLETED", "CrowdedTables|Littering"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "orderWithoutRemarks").containsEntry("orderWithoutRemarks", "true");
	}

	@Test
	void isWithoutRemarksWhenNoPlaceWasAssessed() {
		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, List.of(checkpoint("Tillstånd", "COMPLETED", null)), CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "orderWithoutRemarks").containsEntry("orderWithoutRemarks", "true");
	}

	@Test
	void isWithRemarksForAPlaceWithDeficienciesButNothingObserved() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "NOT_COMPLETED", null),
			place("Toaletter", "NOT_COMPLETED", "").created(CREATED.plusMinutes(1)));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "orderWithRemarks").containsEntry("orderWithRemarks", "true");
	}

	@Test
	void skipsEmptyPartsOfTheObservedThings() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "NOT_COMPLETED", "Smoking||"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "observationSmokingOutdoor", "orderWithRemarks").containsEntry("observationSmokingOutdoor", "X");
	}

	@Test
	void leavesOutAPlaceThatIsNotActive() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null),
			new Measure().type("OBSERVATION").status("CANCELLED").title("Uteservering").result("NOT_COMPLETED").resultText("Smoking").created(CREATED));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures", "orderWithoutRemarks").containsEntry("orderWithoutRemarks", "true");
	}

	@Test
	void sendsNoOrderToTheInternalProtocol() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "NOT_COMPLETED", "Smoking"));

		final var parameters = InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, INTERNAL_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER);

		assertThat(parameters).containsOnlyKeys("caseNumber", "measures");
	}

	@Test
	void failsWithoutRetryOnAnUnknownPlaceWithoutDeficiencies() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Bakgården", "COMPLETED", null));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has an observation of the place 'Bakgården', which is not among the places of the inspection protocol in pw-alkt");
	}

	@Test
	void failsWithoutRetryOnAPlaceWithoutTitle() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place(null, "NOT_COMPLETED", "Smoking"));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has an observation of the place 'null', which is not among the places of the inspection protocol in pw-alkt");
	}

	@Test
	void failsWithoutRetryOnAnUnknownObservedThing() {
		final var measures = List.of(checkpoint("Tillstånd", "COMPLETED", null), place("Uteservering", "NOT_COMPLETED", "Smoking|Fireworks"));

		assertThatThrownBy(() -> InspectionProtocolMapper.toTemplateParameters(ERRAND_ID, SERVING_TEMPLATE, NO_ANSWERS, measures, CASE_NUMBER))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has observed 'Fireworks' at the place 'Uteservering', which is not among the observations of the inspection protocol in pw-alkt");
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
