package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.service.model.ProcessType.CATERING_OCCASION;
import static se.sundsvall.alkt.service.model.ProcessType.LOW_ALCOHOL_BEER_SALES;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String DECISION_ID = "decision-id";
	// Half past midnight in Sweden, still the day before in UTC
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-28T23:30:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private AssetService assetServiceMock;

	@Captor
	private ArgumentCaptor<Decision> decisionCaptor;

	private DecisionService decisionService;

	@BeforeEach
	void setUp() {
		decisionService = new DecisionService(supportManagementIntegrationMock, assetServiceMock, CLOCK);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"APPROVAL", "WARNING", "REJECTION"
	})
	void getDecisionOutcomeAnswersWithTheOutcomeOfTheCompletedDecision(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome(outcome)));

		assertThat(decisionService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(outcome);
	}

	@Test
	void getDecisionOutcomeAnswersWithNullForACompletedDecisionWithoutOutcome() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(new Decision()));

		assertThat(decisionService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("null");
	}

	@Test
	void getDecisionOutcomeAnswersWithNoneWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThat(decisionService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(DECISION_OUTCOME_NONE);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		"alcohol-serving, APPROVAL", "alcohol-serving, APPROVAL_WITH_CONDITIONS", "alcohol-serving, REJECTED", "alcohol-serving, DISMISSED",
		"alcohol-serving, INADMISSIBLE", "alcohol-serving, NONE", "null, APPROVAL", "action-errand, REPRIMAND", "action-errand, WARNING",
		"action-errand, REVOCATION", "action-errand, NONE"
	})
	void isKnownOutcomeKnowsTheOutcomesOfTheProcess(final String processKey, final String outcome) {
		assertThat(decisionService.isKnownOutcome(processKey, outcome)).isTrue();
	}

	/** An outcome outside the set of the process is not known, a near miss of a known one included. */
	@ParameterizedTest
	@CsvSource({
		"alcohol-serving, WARNING", "alcohol-serving, REJECTION", "alcohol-serving, PARTIAL_APPROVAL", "alcohol-serving, approval", "alcohol-serving, null",
		"action-errand, APPROVAL", "action-errand, null"
	})
	void isKnownOutcomeDoesNotKnowTheOutcomesOfAnotherProcess(final String processKey, final String outcome) {
		assertThat(decisionService.isKnownOutcome(processKey, outcome)).isFalse();
	}

	@Test
	void approveAutomaticallyWritesADraftLinksEveryAttachmentAndThenCompletesIt() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().title("Anmälan om försäljning av folköl, Kafé Solsidan"));
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(DECISION_ID);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second")));

		assertThat(decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null)).isEqualTo(DECISION_ID);

		final InOrder inOrder = inOrder(supportManagementIntegrationMock);
		inOrder.verify(supportManagementIntegrationMock).createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), decisionCaptor.capture());
		inOrder.verify(supportManagementIntegrationMock).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "first");
		inOrder.verify(supportManagementIntegrationMock).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "second");
		inOrder.verify(supportManagementIntegrationMock).updateDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(DECISION_ID), decisionCaptor.capture());

		final var draft = decisionCaptor.getAllValues().getFirst();
		assertThat(draft.getStatus()).isEqualTo("DRAFT");
		assertThat(draft.getTitle()).isEqualTo("Anmälan om försäljning av folköl");
		assertThat(draft.getDescription()).isEqualTo("Anmälan om försäljning av folköl, Kafé Solsidan");
		assertThat(draft.getOutcome()).isEqualTo("APPROVAL");
		assertThat(draft.getValidFrom()).isEqualTo(LocalDate.of(2026, 3, 29));
		assertThat(draft.getValidTo()).isNull();
		assertThat(draft.getDecidedAt().toInstant()).isEqualTo(CLOCK.instant());

		final var completion = decisionCaptor.getAllValues().getLast();
		assertThat(completion).isEqualTo(new Decision().status("COMPLETED").decidedAt(draft.getDecidedAt()).completedAt(draft.getDecidedAt()).parameters(null));
	}

	@Test
	void approveAutomaticallyEndsTheDecisionOnTheDateOfTheNamedParameter() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("occasionEndDate").values(List.of("2026-03-30")))));
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), decisionCaptor.capture())).thenReturn(DECISION_ID);

		assertThat(decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CATERING_OCCASION.getKey(), "occasionEndDate")).isEqualTo(DECISION_ID);

		assertThat(decisionCaptor.getValue().getValidFrom()).isEqualTo(LocalDate.of(2026, 3, 29));
		assertThat(decisionCaptor.getValue().getValidTo()).isEqualTo(LocalDate.of(2026, 3, 30));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		"null, has no parameter 'occasionEndDate'",
		"30 mars 2026, is not a date on the form yyyy-MM-dd",
		"2026-03-29, which is not after today"
	})
	void approveAutomaticallyWritesNothingWithoutADateToEndTheDecisionOn(final String value, final String message) {
		final var parameters = Optional.ofNullable(value).map(date -> List.of(new Parameter().key("occasionEndDate").values(List.of(date)))).orElse(List.of());
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().parameters(parameters));

		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CATERING_OCCASION.getKey(), "occasionEndDate"))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(message);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void approveAutomaticallyCompletesTheDraftAnEarlierAttemptLeftAndLinksOnlyWhatIsMissing() {
		final var draft = new Decision().id(DECISION_ID).status("DRAFT").method("AUTOMATIC").decidedBy("pw-alkt").attachments(List.of(new ErrandAttachment().id("first")));
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(draft));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second")));

		assertThat(decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null)).isEqualTo(DECISION_ID);

		verify(supportManagementIntegrationMock, never()).createDecision(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "first");
		verify(supportManagementIntegrationMock).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "second");
		verify(supportManagementIntegrationMock).updateDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(DECISION_ID), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
		assertThat(decisionCaptor.getValue().getDecidedAt()).isNotNull();
		assertThat(decisionCaptor.getValue().getCompletedAt()).isEqualTo(decisionCaptor.getValue().getDecidedAt());
	}

	@Test
	void approveAutomaticallyLeavesACompletedDecisionAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("COMPLETED")));

		assertThat(decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null)).isEqualTo(DECISION_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void approveAutomaticallyLeavesADraftOfACaseWorkerAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new Decision().id("manual").status("DRAFT").method("MANUAL").decidedBy("case-worker").outcome("REJECTED"),
			new Decision().id("automatic").status("DRAFT").method("AUTOMATIC").decidedBy("another-service")));

		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ERRAND_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void approveAutomaticallyLeavesACancelledDecisionAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("CANCELLED")));

		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ERRAND_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void approveAutomaticallyWithoutAttachmentsOnTheErrandOnlyCompletesIt() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(DECISION_ID);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null);

		verify(supportManagementIntegrationMock, never()).linkDecisionAttachment(anyString(), anyString(), anyString(), anyString(), anyString());
		verify(supportManagementIntegrationMock).updateDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(DECISION_ID), any());
	}

	@Test
	void approveAutomaticallyDecidesNothingForAnErrandWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().stakeholders(List.of(new Stakeholder().role("APPLICANT").externalId("applicant-id"), new Stakeholder().role("PRIMARY"))));

		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verify(supportManagementIntegrationMock).getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void approveAutomaticallyLeavesTheDraftOfAnEarlierAttemptUncompletedWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new Decision().id(DECISION_ID).status("DRAFT").method("AUTOMATIC").decidedBy("pw-alkt")));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, LOW_ALCOHOL_BEER_SALES.getKey(), null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock, never()).updateDecision(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void approveAutomaticallyFailsForAProcessWithoutAnAutomaticDecision() {
		assertThatThrownBy(() -> decisionService.approveAutomatically(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING.getKey(), null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ALCOHOL_SERVING.getKey());

		verifyNoInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createChangeDraftChecksThePermitAndDraftsTheChangeOfTheErrand() {
		final var errand = new Errand().parameters(List.of(
			new Parameter().key("assetId").values(List.of("asset-id")),
			new Parameter().key("serveringstid").values(List.of("11.00–02.00"))));
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), decisionCaptor.capture())).thenReturn(DECISION_ID);

		assertThat(decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, ALCOHOL_SERVING_CHANGE.getKey())).isEqualTo(DECISION_ID);

		final InOrder inOrder = inOrder(assetServiceMock, supportManagementIntegrationMock);
		inOrder.verify(assetServiceMock).getPermitToChange(MUNICIPALITY_ID, errand, ERRAND_ID, ALCOHOL_SERVING_PERMIT);
		inOrder.verify(supportManagementIntegrationMock).createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any());
		assertThat(decisionCaptor.getValue().getStatus()).isEqualTo("DRAFT");
		assertThat(decisionCaptor.getValue().getTitle()).isEqualTo("Ändring av serveringstillstånd");
		assertThat(decisionCaptor.getValue().getParameters()).extracting(Parameter::getKey).containsExactly("serveringstid");
	}

	@Test
	void createChangeDraftFailsForAProcessWithoutAChangeDraft() {
		assertThatThrownBy(() -> decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, ALCOHOL_SERVING.getKey()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ALCOHOL_SERVING.getKey());

		verifyNoInteractions(supportManagementIntegrationMock, assetServiceMock);
	}

	/** A rerun after a lost answer finds the draft of the earlier attempt. */
	@Test
	void createChangeDraftAnswersWithTheDecisionAlreadyOnTheErrand() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("DRAFT")));

		assertThat(decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, ALCOHOL_SERVING_CHANGE.getKey())).isEqualTo(DECISION_ID);

		verifyNoMoreInteractions(supportManagementIntegrationMock);
		verifyNoInteractions(assetServiceMock);
	}

	@Test
	void createChangeDraftDraftsNothingWhenThePermitCannotBeChanged() {
		final var errand = new Errand();
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(assetServiceMock.getPermitToChange(MUNICIPALITY_ID, errand, ERRAND_ID, ALCOHOL_SERVING_PERMIT)).thenThrow(new NonRetryableException("Errand 'errand-id' names no asset to change"));

		assertThatThrownBy(() -> decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, ALCOHOL_SERVING_PERMIT, ALCOHOL_SERVING_CHANGE.getKey()))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names no asset to change");

		verify(supportManagementIntegrationMock, never()).createDecision(anyString(), anyString(), anyString(), any());
	}

	private static Errand errandWithPermitHolder() {
		return new Errand().stakeholders(List.of(new Stakeholder().role("PRIMARY").externalId("party-id")));
	}
}
