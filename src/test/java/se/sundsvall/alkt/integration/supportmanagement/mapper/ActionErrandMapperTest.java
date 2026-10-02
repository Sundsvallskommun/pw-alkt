package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ActionErrandMapper.toActionErrand;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ActionErrandMapper.toReferredFrom;

class ActionErrandMapperTest {

	@Test
	void toActionErrandCarriesTheInspectionOver() {
		final var dueAt = OffsetDateTime.parse("2026-10-15T00:00:00+02:00");
		final var permitHolder = new Stakeholder().role("PRIMARY").externalId("party-id");
		final var inspection = new Errand()
			.id("inspection-id")
			.title("Brister vid tillsyn")
			.description("Följande brister ska åtgärdas")
			.stakeholders(List.of(permitHolder, new Stakeholder().role("CONTACT").externalId("contact-id")))
			.measures(List.of(new Measure().id("measure-id").type("DEFICIENCY").title("Matsedel saknas").description("Matsedel ska finnas").goal("Matsedel finns").dueAt(dueAt)
				.addedByUser("handlaggare").addedByRole("CASE_WORKER").created(dueAt)));

		final var result = toActionErrand(inspection, "INSPECTION", "ACTION_ERRAND");

		assertThat(result.getId()).isNull();
		assertThat(result.getTitle()).isEqualTo("Brister vid tillsyn");
		assertThat(result.getDescription()).isEqualTo("Följande brister ska åtgärdas");
		assertThat(result.getClassification()).isEqualTo(new Classification().category("INSPECTION").type("ACTION_ERRAND"));
		assertThat(result.getStakeholders()).containsExactly(permitHolder);
		assertThat(result.getMeasures()).containsExactly(new Measure().type("DEFICIENCY").title("Matsedel saknas").description("Matsedel ska finnas").goal("Matsedel finns").dueAt(dueAt)
			.addedByUser("handlaggare").addedByRole("CASE_WORKER"));
		assertThat(result.getExternalTags()).containsExactly(new ExternalTag().key("inspectionErrandId").value("inspection-id"));
	}

	@Test
	void toActionErrandOfAnInspectionWithoutStakeholdersOrMeasures() {
		final var result = toActionErrand(new Errand().id("inspection-id"), "INSPECTION", "ACTION_ERRAND");

		assertThat(result.getStakeholders()).isEmpty();
		assertThat(result.getMeasures()).isEmpty();
	}

	@Test
	void toReferredFromLinksFromTheInspection() {
		assertThat(toReferredFrom("inspection-id", "ALKT")).isEqualTo("LINK|inspection-id;errand;support-management;ALKT|");
	}
}
