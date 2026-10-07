package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.ProcessActivity;
import generated.se.sundsvall.supportmanagement.ProcessError;
import generated.se.sundsvall.supportmanagement.ProcessSignal;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;

class SupportManagementMapperTest {

	@Test
	void toReportTarget() {
		final var externalTask = mock(ExternalTask.class);
		final var errandId = UUID.randomUUID().toString();
		final var processInstanceId = UUID.randomUUID().toString();
		final var externalTaskId = UUID.randomUUID().toString();
		when(externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		when(externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn("ALKT");
		when(externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(errandId);
		when(externalTask.getProcessInstanceId()).thenReturn(processInstanceId);
		when(externalTask.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTask.getId()).thenReturn(externalTaskId);

		assertThat(SupportManagementMapper.toReportTarget(externalTask))
			.isEqualTo(new ReportTarget("2281", "ALKT", errandId, processInstanceId, "alcohol-serving", externalTaskId));
	}

	@Test
	void toErrandProcessReport() {
		final var externalTaskId = UUID.randomUUID().toString();
		final var target = new ReportTarget("2281", "ALKT", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "alcohol-serving", externalTaskId);
		final var occurredAt = OffsetDateTime.now();
		final var activity = new ProcessActivity().activityType("INCIDENT").activityId("investigation_phase").severity("ERROR").occurredAt(occurredAt);
		final var error = new ProcessError().code("INCIDENT").message("Timeout");
		final var report = new ProcessStateReport(FAILED, "investigation_phase", "Investigation", 7L, error, List.of(activity), List.of(), Map.of(), null, false);

		final var result = SupportManagementMapper.toErrandProcessReport(target, report);

		assertThat(result.getProcessService()).isEqualTo("pw-alkt");
		assertThat(result.getProcessKey()).isEqualTo("alcohol-serving");
		assertThat(result.getProcessInstanceId()).isNull();
		assertThat(result.getProcessStatus()).isEqualTo("FAILED");
		assertThat(result.getCurrentActivityId()).isEqualTo("investigation_phase");
		assertThat(result.getCurrentActivityName()).isEqualTo("Investigation");
		assertThat(result.getExternalTaskId()).isEqualTo(externalTaskId);
		assertThat(result.getErrandVersion()).isEqualTo(7L);
		assertThat(result.getError()).isSameAs(error);
		assertThat(result.getActivities()).containsExactly(activity);
		assertThat(result.getAwaitingSignals()).isEmpty();
	}

	@Test
	void toErrandProcessFromAWaitStateReport() {
		final var target = new ReportTarget("2281", "ALKT", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "alcohol-serving", null);
		final var signal = new AwaitingSignal("review_completed", "Review completed");

		final var result = SupportManagementMapper.toErrandProcessReport(target, ProcessStateReport.waiting("review_phase", "Review").withAwaitingSignals(List.of(signal)));

		assertThat(result.getProcessStatus()).isEqualTo("WAITING");
		assertThat(result.getCurrentActivityId()).isEqualTo("review_phase");
		assertThat(result.getCurrentActivityName()).isEqualTo("Review");
		assertThat(result.getAwaitingSignals())
			.extracting(ProcessSignal::getName, ProcessSignal::getLabel)
			.containsExactly(tuple("review_completed", "Review completed"));
	}

	@Test
	void toErrandProcessFromACompletedReport() {
		final var target = new ReportTarget("2281", "ALKT", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "external-inspection", null);

		final var result = SupportManagementMapper.toErrandProcessReport(target, ProcessStateReport.completed());

		assertThat(result.getProcessStatus()).isEqualTo("COMPLETED");
		assertThat(result.getCurrentActivityId()).isNull();
		assertThat(result.getExternalTaskId()).isNull();
		assertThat(result.getErrandVersion()).isNull();
		assertThat(result.getError()).isNull();
		assertThat(result.getActivities()).isEmpty();
		assertThat(result.getAwaitingSignals()).isEmpty();
	}

	@Test
	void toDecisionTitleNamesThePermitOfEachLowAlcoholBeerProcess() {
		assertThat(SupportManagementMapper.toDecisionTitle(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)).isEqualTo("Anmälan om försäljning av folköl");
		assertThat(SupportManagementMapper.toDecisionTitle(PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING)).isEqualTo("Anmälan om servering av folköl");
		assertThat(SupportManagementMapper.toDecisionTitle(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING)).isEqualTo("Anmälan om försäljning och servering av folköl");
	}

	@Test
	void toDecisionTitleRefusesAProcessWithoutAnAutomaticDecision() {
		assertThatThrownBy(() -> SupportManagementMapper.toDecisionTitle(PROCESS_KEY_ALCOHOL_SERVING))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(PROCESS_KEY_ALCOHOL_SERVING)
			.hasMessageContaining(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)
			.hasMessageContaining(PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING)
			.hasMessageContaining(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING);
		assertThatThrownBy(() -> SupportManagementMapper.toDecisionTitle(null))
			.isInstanceOf(NonRetryableException.class);
	}

	@Test
	void toChangeDraftCarriesTheChangeOfTheErrandAsAnAutomaticDraft() {
		final var decidedAt = OffsetDateTime.parse("2026-10-05T10:00:00+02:00");
		final var errand = new Errand()
			.title("Ändring av serveringstillstånd, Runt Hörnet")
			.parameters(List.of(
				new Parameter().key("assetId").values(List.of("9c8b7a6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")),
				new Parameter().key("conditions").values(List.of("Inga villkor")),
				new Parameter().key("errandId").values(List.of()),
				new Parameter().key("legalBasis").values(List.of("Från kunden")),
				new Parameter().key("serveringstid").values(List.of("11.00–02.00")),
				new Parameter().key("uteservering").values(List.of())));

		final var result = SupportManagementMapper.toChangeDraft(errand, decidedAt);

		assertThat(result.getType()).isEqualTo("PERMIT");
		assertThat(result.getStatus()).isEqualTo("DRAFT");
		assertThat(result.getMethod()).isEqualTo("AUTOMATIC");
		assertThat(result.getDecidedBy()).isEqualTo("pw-alkt");
		assertThat(result.getDecidedAt()).isEqualTo(decidedAt);
		assertThat(result.getOutcome()).isEqualTo("APPROVAL");
		assertThat(result.getTitle()).isEqualTo("Ändring av serveringstillstånd");
		assertThat(result.getDescription()).isEqualTo("Ändring av serveringstillstånd, Runt Hörnet");
		assertThat(result.getParameters()).extracting(Parameter::getKey).containsExactly("serveringstid", "uteservering");
	}

	/** The premises and the case worker's choice of restaurant number belong to the errand, not to the permit. */
	@Test
	void toChangeDraftLeavesOutThePremisesAndTheRestaurantNumberChoice() {
		final var errand = new Errand().parameters(List.of(
			new Parameter().key("premisesName").values(List.of("Runt Hörnet")),
			new Parameter().key("premisesStreetAddress").values(List.of("Storgatan 33")),
			new Parameter().key("premisesPostalCode").values(List.of("852 30")),
			new Parameter().key("premisesPostalArea").values(List.of("Sundsvall")),
			new Parameter().key("restaurantNumber").values(List.of("22810001")),
			new Parameter().key("newRestaurantNumber").values(List.of("true")),
			new Parameter().key("premisesRestaurantNumber").values(List.of("22810009")),
			new Parameter().key("serveringstid").values(List.of("11.00–02.00"))));

		assertThat(SupportManagementMapper.toChangeDraft(errand, OffsetDateTime.now()).getParameters()).extracting(Parameter::getKey).containsExactly("serveringstid");
	}

	@Test
	void toChangeDraftOfAnErrandWithoutParametersHasNone() {
		assertThat(SupportManagementMapper.toChangeDraft(new Errand().parameters(null), OffsetDateTime.now()).getParameters()).isEmpty();
	}

	@Test
	void toRemovedParameterKeysAreThoseWithoutAValue() {
		final var decision = new Decision().parameters(List.of(
			new Parameter().key("serveringstid").values(List.of("11.00–02.00")),
			new Parameter().key("uteservering").values(List.of()),
			new Parameter().key("ordningsvakt").values(List.of(" ", "")),
			new Parameter().key("matsal")));

		assertThat(SupportManagementMapper.toRemovedParameterKeys(decision)).containsExactlyInAnyOrder("uteservering", "ordningsvakt", "matsal");
		assertThat(SupportManagementMapper.toRemovedParameterKeys(new Decision().parameters(null))).isEmpty();
	}

	@Test
	void toAutomaticDecisionApprovesUntilFurtherNotice() {
		final var validFrom = LocalDate.of(2026, 9, 24);
		final var decidedAt = OffsetDateTime.now();

		final var result = SupportManagementMapper.toAutomaticDecision("Anmälan om servering av folköl", new Errand().title("Anmälan om servering av folköl, Kafé Solsidan"), validFrom, decidedAt);

		assertThat(result.getType()).isEqualTo("PERMIT");
		assertThat(result.getStatus()).isEqualTo("DRAFT");
		assertThat(result.getMethod()).isEqualTo("AUTOMATIC");
		assertThat(result.getDecidedBy()).isEqualTo("pw-alkt");
		assertThat(result.getOutcome()).isEqualTo("APPROVAL");
		assertThat(result.getTitle()).isEqualTo("Anmälan om servering av folköl");
		assertThat(result.getDescription()).isEqualTo("Anmälan om servering av folköl, Kafé Solsidan");
		assertThat(result.getValidFrom()).isEqualTo(validFrom);
		assertThat(result.getValidTo()).isNull();
		assertThat(result.getDecidedAt()).isEqualTo(decidedAt);
		assertThat(result.getCompletedAt()).isNull();
	}

	@Test
	void toDecisionCompletionCarriesOnlyWhatCompletesTheDecision() {
		final var decidedAt = OffsetDateTime.now();

		final var result = SupportManagementMapper.toDecisionCompletion(decidedAt);

		assertThat(result).isEqualTo(new Decision().status("COMPLETED").decidedAt(decidedAt).completedAt(decidedAt).parameters(null));
		assertThat(result.getParameters()).isNull();
	}

	@Test
	void toParameterValuesJoinsTheValuesOfEachParameterLeavingOutBlankOnes() {
		final var parameters = List.of(
			new Parameter().key("permitHolderName").values(List.of("Runt Hörnet AB")),
			new Parameter().key("serveringsyta").values(List.of("Matsalen", " ", "Uteserveringen")),
			new Parameter().key("premisesName").values(List.of(" ")),
			new Parameter().key("premisesPhone"));

		assertThat(SupportManagementMapper.toParameterValues(parameters)).containsExactly(
			entry("permitHolderName", "Runt Hörnet AB"),
			entry("serveringsyta", "Matsalen, Uteserveringen"));
	}

	@Test
	void toParameterValuesIsEmptyWithoutParameters() {
		assertThat(SupportManagementMapper.toParameterValues(null)).isEmpty();
	}

	@Test
	void toErrandRelationPointsAtTheErrandAndLeavesTheTarget() {
		assertThat(SupportManagementMapper.toErrandRelation("LINK", "errand-id", "ALKT")).isEqualTo("LINK|errand-id;case;supportmanagement;ALKT|");
	}

	@Test
	void toPartyIdTakesTheExternalIdOfThePermitHolder() {
		final var errand = new Errand().stakeholders(List.of(
			new Stakeholder().role("APPLICANT").externalId("applicant-id"),
			new Stakeholder().role("PRIMARY"),
			new Stakeholder().role("PRIMARY").externalId("holder-id")));

		assertThat(SupportManagementMapper.toPartyId(errand)).contains("holder-id");
	}

	@Test
	void toPartyIdIsEmptyWithoutStakeholders() {
		assertThat(SupportManagementMapper.toPartyId(new Errand().stakeholders(null))).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(value = {
		"PRIMARY, holder-id, true",
		"PRIMARY, null, false",
		"APPLICANT, applicant-id, false"
	}, nullValues = "null")
	void isPermitHolderTakesThePrimaryStakeholderWithAnExternalId(final String role, final String externalId, final boolean expected) {
		assertThat(SupportManagementMapper.isPermitHolder(new Stakeholder().role(role).externalId(externalId))).isEqualTo(expected);
	}

	@Test
	void toConditionsPutsTheTermsOnePerLineInTheirOrderLeavingOutBlankOnes() {
		final var decision = new Decision().terms(List.of(
			new DecisionTerm().text("Ordningsvakt ska finnas efter 23.00."),
			new DecisionTerm().sortOrder(2).text("Godkänd matsal ska finnas."),
			new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat."),
			new DecisionTerm().sortOrder(3).text(" ")));

		assertThat(SupportManagementMapper.toConditions(decision))
			.isEqualTo("Serveringsområdet ska vara avgränsat.\nGodkänd matsal ska finnas.\nOrdningsvakt ska finnas efter 23.00.");
	}

	@Test
	void toConditionsIsEmptyForADecisionWithoutTerms() {
		assertThat(SupportManagementMapper.toConditions(new Decision().terms(null))).isEmpty();
	}
}
