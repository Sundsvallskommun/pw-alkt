package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
