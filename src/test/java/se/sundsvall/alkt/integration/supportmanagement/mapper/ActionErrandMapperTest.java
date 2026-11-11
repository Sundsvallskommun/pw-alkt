package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static generated.se.sundsvall.supportmanagement.Priority.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ActionErrandMapper.toActionErrand;

class ActionErrandMapperTest {

	@Test
	void toActionErrandCarriesTheInspectionOver() {
		final var dueAt = OffsetDateTime.parse("2026-10-15T00:00:00+02:00");
		final var permitHolder = new Stakeholder().role("PRIMARY").externalId("party-id");
		final var inspection = new Errand()
			.id("inspection-id")
			.title("Brister vid tillsyn")
			.assignedUserId("handlaggare")
			.description("Följande brister ska åtgärdas")
			.classification(new Classification().category("AOT").type("AOT.UNCATEGORIZED"))
			.labels(List.of(new ErrandLabel().id("inspection-label-id")))
			.stakeholders(List.of(permitHolder, new Stakeholder().role("CONTACT").externalId("contact-id"), new Stakeholder().role("PRIMARY")))
			.measures(List.of(new Measure().id("measure-id").type("EXTERNAL").title("Matsedel saknas").description("Matsedel ska finnas").goal("Matsedel finns").dueAt(dueAt)
				.addedByUser("handlaggare").addedByRole("CASE_WORKER").created(dueAt).status("ACTIVE"),
				new Measure().type("EXTERNAL").title("Alkoholpolicy saknas").status("DRAFT"),
				new Measure().type("EXTERNAL").title("Serveringsansvarig saknas").status("COMPLETED"),
				new Measure().type("EXTERNAL").title("Ordningsvakt saknas").status("CANCELLED"),
				new Measure().type("EXTERNAL").title("Brandskydd bristfälligt"),
				new Measure().type("INTERNAL").title("Brandskydd (8:16)").status("ACTIVE"),
				new Measure().type("OBSERVATION").title("Uteservering").status("ACTIVE")));

		final var result = toActionErrand(inspection, "errand-id", "label-id");

		assertThat(result.getId()).isNull();
		assertThat(result.getTitle()).isEqualTo("Åtgärdsärende");
		assertThat(result.getStatus()).isEqualTo("NEW");
		assertThat(result.getPriority()).isEqualTo(MEDIUM);
		assertThat(result.getChannel()).isEqualTo("WEB_UI");
		assertThat(result.getReporterUserId()).isEqualTo("handlaggare");
		assertThat(result.getDescription()).isEqualTo("Följande brister ska åtgärdas");
		assertThat(result.getClassification()).isNull();
		assertThat(result.getLabels()).containsExactly(new ErrandLabel().id("label-id"));
		assertThat(result.getStakeholders()).containsExactly(permitHolder);
		assertThat(result.getMeasures()).containsExactly(new Measure().type("EXTERNAL").title("Matsedel saknas").description("Matsedel ska finnas").goal("Matsedel finns").dueAt(dueAt)
			.addedByUser("handlaggare").addedByRole("CASE_WORKER"));
		assertThat(result.getExternalTags()).containsExactly(new ExternalTag().key("inspectionErrandId").value("errand-id"));
		assertThat(result.getPhases()).isNull();
		assertThat(result.getActiveNotifications()).isNull();
		assertThat(result.getActions()).isNull();
	}

	@Test
	void toActionErrandOfAnInspectionWithoutStakeholdersOrMeasures() {
		final var result = toActionErrand(new Errand().id("inspection-id"), "inspection-id", "label-id");

		assertThat(result.getReporterUserId()).isNull();
		assertThat(result.getStakeholders()).isEmpty();
		assertThat(result.getMeasures()).isEmpty();
	}
}
