package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String DECISION_ID = "decision-id";
	private static final String PARTY_ID = "party-id";
	private static final String ASSET_ID = "asset-id";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyAssetsIntegration partyAssetsIntegrationMock;

	@Captor
	private ArgumentCaptor<AssetCreateRequest> assetCaptor;

	@Captor
	private ArgumentCaptor<AssetFile> attachmentCaptor;

	@InjectMocks
	private AssetService assetService;

	@Test
	void getDecisionOutcomeAnswersWithTheOutcomeOfTheCompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome("APPROVAL")));

		assertThat(assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("APPROVAL");
	}

	@Test
	void getDecisionOutcomeAnswersWithRejection() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome("REJECTION")));

		assertThat(assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("REJECTION");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"PARTIAL_APPROVAL", "approval"
	})
	void getDecisionOutcomeFailsOnAnOutcomeItDoesNotKnow(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome(outcome)));

		assertThatThrownBy(() -> assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("expected one of [APPROVAL, REJECTION]");
	}

	@Test
	void getDecisionOutcomeAnswersWithNoneWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThat(assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(DECISION_OUTCOME_NONE);
	}

	@Test
	void findOrCreateAssetCreatesADraftAttachesTheFilesOneAtATimeAndActivatesIt() throws IOException {
		final var first = new ErrandAttachment().id("first").fileName("beslut.pdf").mimeType("application/pdf");
		final var second = new ErrandAttachment().id("second").fileName("ritning.pdf").mimeType("application/pdf");
		final var content = "file".getBytes();

		givenADraftFor(approval().type("PERMIT").attachments(List.of(first, second)));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn(content);
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second")).thenReturn(content);

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(supportManagementIntegrationMock, partyAssetsIntegrationMock);
		inOrder.verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), assetCaptor.capture());
		inOrder.verify(supportManagementIntegrationMock).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first");
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(supportManagementIntegrationMock).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).removeDraftAsset(any(), any());

		assertThat(assetCaptor.getValue().getAssetId()).isEqualTo(DECISION_ID);
		assertThat(assetCaptor.getValue().getPartyId()).isEqualTo(PARTY_ID);
		assertThat(attachmentCaptor.getAllValues()).extracting(file -> file.file().getOriginalFilename()).containsExactly("beslut.pdf", "ritning.pdf");
		assertThat(attachmentCaptor.getAllValues().getFirst().file().getBytes()).isEqualTo(content);
	}

	@Test
	void findOrCreateAssetAnswersWithTheExistingAssetOfTheDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of("existing-asset-id"));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("existing-asset-id");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetWithoutAttachmentsOnTheDecision() {
		givenADraftFor(approval().attachments(null));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenAnAttachmentCannotBeFetched() {
		givenADraftFor(approval().attachments(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second"))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first"))
			.thenThrow(Problem.valueOf(BAD_GATEWAY, "Support management is down"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Support management is down");

		verify(supportManagementIntegrationMock, never()).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenAnAttachmentFails() {
		givenADraftFor(approval().attachments(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second"))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn("file".getBytes());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), any());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Party assets is down");

		verify(supportManagementIntegrationMock, never()).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenTheActivationFails() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Party assets is down");

		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetCarriesTheAssetIdWhenTheDraftCannotBeRemoved() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is still down")).when(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(ASSET_ID)
			.hasMessageContaining("Party assets is down")
			.hasMessageContaining("Party assets is still down");
	}

	@Test
	void findOrCreateAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsOnARejection() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome("REJECTION")));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created from APPROVAL");

		verifyNoInteractions(partyAssetsIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsOnADecisionWithoutId() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().id(null)));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no id");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(STAKEHOLDER_ROLE_PERMIT_HOLDER);

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	private void givenADraftFor(final Decision decision) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		when(partyAssetsIntegrationMock.createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any())).thenReturn(ASSET_ID);
	}

	private static Decision approval() {
		return new Decision().id(DECISION_ID).outcome("APPROVAL");
	}

	private static Errand errandWithPermitHolder() {
		return new Errand().stakeholders(List.of(new Stakeholder().role(STAKEHOLDER_ROLE_PERMIT_HOLDER).externalId(PARTY_ID)));
	}
}
