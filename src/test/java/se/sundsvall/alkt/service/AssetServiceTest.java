package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String DECISION_ID = "decision-id";
	private static final String PARTY_ID = "party-id";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyAssetsIntegration partyAssetsIntegrationMock;

	@Captor
	private ArgumentCaptor<AssetCreateRequest> assetCaptor;

	@Captor
	private ArgumentCaptor<List<AssetFile>> attachmentsCaptor;

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
	void createAssetBuildsTheAssetFromTheDecisionWithItsAttachments() {
		final var attachment = new ErrandAttachment().id("attachment-id").fileName("beslut.pdf").mimeType("application/pdf");
		final var decision = approval().type("PERMIT").attachments(List.of(attachment));
		final var content = "file".getBytes();

		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "attachment-id")).thenReturn(content);
		when(partyAssetsIntegrationMock.createAsset(any(), any(), any())).thenReturn("asset-id");

		assertThat(assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("asset-id");

		verify(partyAssetsIntegrationMock).createAsset(eq(MUNICIPALITY_ID), assetCaptor.capture(), attachmentsCaptor.capture());
		assertThat(assetCaptor.getValue().getAssetId()).isEqualTo(DECISION_ID);
		assertThat(assetCaptor.getValue().getPartyId()).isEqualTo(PARTY_ID);
		assertThat(attachmentsCaptor.getValue()).singleElement().satisfies(file -> {
			assertThat(file.file().getOriginalFilename()).isEqualTo("beslut.pdf");
			assertThat(file.file().getBytes()).isEqualTo(content);
		});
	}

	@Test
	void createAssetAnswersWithTheExistingAssetOfTheDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of("existing-asset-id"));

		assertThat(assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("existing-asset-id");

		verify(partyAssetsIntegrationMock, never()).createAsset(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void createAssetWithoutAttachmentsOnTheDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().attachments(null)));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.empty());
		when(partyAssetsIntegrationMock.createAsset(any(), any(), any())).thenReturn("asset-id");

		assertThat(assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo("asset-id");

		verify(partyAssetsIntegrationMock).createAsset(eq(MUNICIPALITY_ID), any(), attachmentsCaptor.capture());
		assertThat(attachmentsCaptor.getValue()).isEmpty();
	}

	@Test
	void createAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void createAssetFailsOnARejection() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome("REJECTION")));

		assertThatThrownBy(() -> assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created from APPROVAL");

		verifyNoInteractions(partyAssetsIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void createAssetFailsOnADecisionWithoutId() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().id(null)));

		assertThatThrownBy(() -> assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no id");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void createAssetFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> assetService.createAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(STAKEHOLDER_ROLE_PERMIT_HOLDER);

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	private static Decision approval() {
		return new Decision().id(DECISION_ID).outcome("APPROVAL");
	}

	private static Errand errandWithPermitHolder() {
		return new Errand().stakeholders(List.of(new Stakeholder().role(STAKEHOLDER_ROLE_PERMIT_HOLDER).externalId(PARTY_ID)));
	}
}
