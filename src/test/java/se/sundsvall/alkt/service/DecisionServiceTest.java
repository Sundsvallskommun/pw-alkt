package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Parameter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
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
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String DECISION_ID = "decision-id";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private AssetService assetServiceMock;

	@Captor
	private ArgumentCaptor<Decision> decisionCaptor;

	@InjectMocks
	private DecisionService decisionService;

	@Test
	void createDecisionWritesADraftLinksEveryAttachmentAndThenCompletesIt() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand().title("Anmälan om försäljning av folköl, Kafé Solsidan"));
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(DECISION_ID);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second")));

		assertThat(decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)).isEqualTo(DECISION_ID);

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
		assertThat(draft.getValidFrom()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Stockholm")));
		assertThat(draft.getValidTo()).isNull();
		assertThat(draft.getDecidedAt()).isNotNull();

		final var completion = decisionCaptor.getAllValues().getLast();
		assertThat(completion).isEqualTo(new Decision().status("COMPLETED").decidedAt(draft.getDecidedAt()).completedAt(draft.getDecidedAt()).parameters(null));
	}

	@Test
	void createDecisionCompletesTheDraftAnEarlierAttemptLeftAndLinksOnlyWhatIsMissing() {
		final var draft = new Decision().id(DECISION_ID).status("DRAFT").method("AUTOMATIC").decidedBy("pw-alkt").attachments(List.of(new ErrandAttachment().id("first")));
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(draft));
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second")));

		assertThat(decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)).isEqualTo(DECISION_ID);

		verify(supportManagementIntegrationMock, never()).createDecision(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getErrand(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "first");
		verify(supportManagementIntegrationMock).linkDecisionAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, DECISION_ID, "second");
		verify(supportManagementIntegrationMock).updateDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(DECISION_ID), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
		assertThat(decisionCaptor.getValue().getDecidedAt()).isNotNull();
		assertThat(decisionCaptor.getValue().getCompletedAt()).isEqualTo(decisionCaptor.getValue().getDecidedAt());
	}

	@Test
	void createDecisionLeavesACompletedDecisionAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("COMPLETED")));

		assertThat(decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES)).isEqualTo(DECISION_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createDecisionLeavesADraftOfACaseWorkerAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new Decision().id("manual").status("DRAFT").method("MANUAL").decidedBy("case-worker").outcome("REJECTED"),
			new Decision().id("automatic").status("DRAFT").method("AUTOMATIC").decidedBy("another-service")));

		assertThatThrownBy(() -> decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ERRAND_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createDecisionLeavesACancelledDecisionAlone() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("CANCELLED")));

		assertThatThrownBy(() -> decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ERRAND_ID);

		verify(supportManagementIntegrationMock).getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createDecisionWithoutAttachmentsOnTheErrandOnlyCompletesIt() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());
		when(supportManagementIntegrationMock.createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(DECISION_ID);
		when(supportManagementIntegrationMock.getAttachments(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());

		decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SALES);

		verify(supportManagementIntegrationMock, never()).linkDecisionAttachment(anyString(), anyString(), anyString(), anyString(), anyString());
		verify(supportManagementIntegrationMock).updateDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(DECISION_ID), any());
	}

	@Test
	void createDecisionFailsForAProcessWithoutAnAutomaticDecision() {
		assertThatThrownBy(() -> decisionService.createDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(PROCESS_KEY_ALCOHOL_SERVING);

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

		assertThat(decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PERMIT_TYPE_ALCOHOL_SERVING)).isEqualTo(DECISION_ID);

		final InOrder inOrder = inOrder(assetServiceMock, supportManagementIntegrationMock);
		inOrder.verify(assetServiceMock).getPermitToChange(MUNICIPALITY_ID, errand, ERRAND_ID, PERMIT_TYPE_ALCOHOL_SERVING);
		inOrder.verify(supportManagementIntegrationMock).createDecision(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any());
		assertThat(decisionCaptor.getValue().getStatus()).isEqualTo("DRAFT");
		assertThat(decisionCaptor.getValue().getParameters()).extracting(Parameter::getKey).containsExactly("serveringstid");
	}

	/** A rerun after a lost answer finds the draft of the earlier attempt. */
	@Test
	void createChangeDraftAnswersWithTheDecisionAlreadyOnTheErrand() {
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(new Decision().id(DECISION_ID).status("DRAFT")));

		assertThat(decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PERMIT_TYPE_ALCOHOL_SERVING)).isEqualTo(DECISION_ID);

		verifyNoMoreInteractions(supportManagementIntegrationMock);
		verifyNoInteractions(assetServiceMock);
	}

	@Test
	void createChangeDraftDraftsNothingWhenThePermitCannotBeChanged() {
		final var errand = new Errand();
		when(supportManagementIntegrationMock.getDecisions(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of());
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errand);
		when(assetServiceMock.getPermitToChange(MUNICIPALITY_ID, errand, ERRAND_ID, PERMIT_TYPE_ALCOHOL_SERVING)).thenThrow(new NonRetryableException("Errand 'errand-id' names no asset to change"));

		assertThatThrownBy(() -> decisionService.createChangeDraft(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PERMIT_TYPE_ALCOHOL_SERVING))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names no asset to change");

		verify(supportManagementIntegrationMock, never()).createDecision(anyString(), anyString(), anyString(), any());
	}
}
