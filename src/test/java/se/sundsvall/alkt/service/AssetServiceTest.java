package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
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
	private static final String ASSET_ID = "9c8b7a6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d";
	private static final String CERTIFICATE_TEMPLATE = "permit.serving.certificate";
	private static final String VERSION = "\"3\"";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	@Mock
	private PartyAssetsIntegration partyAssetsIntegrationMock;

	@Mock
	private TemplatingIntegration templatingIntegrationMock;

	@Captor
	private ArgumentCaptor<AssetCreateRequest> assetCaptor;

	@Captor
	private ArgumentCaptor<AssetFile> attachmentCaptor;

	@Captor
	private ArgumentCaptor<AssetUpdateRequest> updateCaptor;

	@Captor
	private ArgumentCaptor<Map<String, Object>> templateParametersCaptor;

	@InjectMocks
	private AssetService assetService;

	@ParameterizedTest
	@ValueSource(strings = {
		"APPROVAL", "APPROVAL_WITH_CONDITIONS", "REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void getDecisionOutcomeAnswersWithTheOutcomeOfTheCompletedDecision(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome(outcome)));

		assertThat(assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).isEqualTo(outcome);
	}

	/** REJECTION was the outcome of a rejection before REJECTED took its place. */
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTION", "PARTIAL_APPROVAL", "approval"
	})
	void getDecisionOutcomeFailsOnAnOutcomeItDoesNotKnow(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().outcome(outcome)));

		assertThatThrownBy(() -> assetService.getDecisionOutcome(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("expected one of [APPROVAL, APPROVAL_WITH_CONDITIONS, DISMISSED, INADMISSIBLE, REJECTED]");
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

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null)).isEqualTo(ASSET_ID);

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
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void findOrCreateAssetAttachesTheCertificateRenderedFromTheDecisionBeforeActivating() throws IOException {
		final var attachment = new ErrandAttachment().id("first").fileName("beslut.pdf").mimeType("application/pdf");
		final var pdf = "%PDF-1.7".getBytes();

		givenADraftFor(approval()
			.attachments(List.of(attachment))
			.parameters(List.of(new Parameter().key("caseNumber").values(List.of("IAN-2026-00209")), new Parameter().key("permitHolderName").values(List.of("Runt Hörnet AB"))))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Serveringsområdet ska vara avgränsat."))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first")).thenReturn("file".getBytes());
		when(templatingIntegrationMock.renderPdf(MUNICIPALITY_ID, CERTIFICATE_TEMPLATE,
			Map.of("caseNumber", "IAN-2026-00209", "permitHolderName", "Runt Hörnet AB", "conditions", "Serveringsområdet ska vara avgränsat.")))
			.thenReturn(pdf);

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE)).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(templatingIntegrationMock, partyAssetsIntegrationMock);
		inOrder.verify(partyAssetsIntegrationMock).createDraftAsset(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), any());
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any());
		inOrder.verify(partyAssetsIntegrationMock).addAttachmentToDraft(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);

		final var certificate = attachmentCaptor.getAllValues().getLast();
		assertThat(certificate.file().getOriginalFilename()).isEqualTo("tillstandsbevis.pdf");
		assertThat(certificate.file().getBytes()).isEqualTo(pdf);
		assertThat(certificate.category()).isEqualTo("Tillståndsbevis");
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenTheCertificateCannotBeRendered() {
		givenADraftFor(approval());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenThrow(new ClientProblem(BAD_GATEWAY, "Templating is down"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Templating is down");

		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	/** A decision that does not fill the template is the case worker's to fix, so the draft goes and no retry follows. */
	@Test
	void findOrCreateAssetRemovesTheDraftAndFailsWithoutRetryWhenTheDecisionDoesNotFillTheCertificate() {
		givenADraftFor(approval());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any()))
			.thenThrow(new NonRetryableException("Missing template parameter 'premisesName'"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Missing template parameter 'premisesName'");

		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	/** The asset id is the only way to find the draft left behind, so it travels with the incident as well. */
	@Test
	void findOrCreateAssetCarriesTheAssetIdWithoutRetryWhenTheDraftOfAnUnfilledCertificateCannotBeRemoved() {
		givenADraftFor(approval());
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any()))
			.thenThrow(new NonRetryableException("Missing template parameter 'premisesName'"));
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(ASSET_ID)
			.hasMessageContaining("Missing template parameter 'premisesName'")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");
	}

	/** A blank parameter names no template, just as a missing one. */
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"", " "
	})
	void findOrCreateAssetCreatesNoCertificateWithoutATemplate(final String certificateTemplate) {
		givenADraftFor(approval());

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, certificateTemplate)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void findOrCreateAssetAnswersWithTheExistingAssetOfTheDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder());
		when(partyAssetsIntegrationMock.findAssetId(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(Optional.of("existing-asset-id"));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null)).isEqualTo("existing-asset-id");

		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetWithoutAttachmentsOnTheDecision() {
		givenADraftFor(approval().attachments(null));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		verify(partyAssetsIntegrationMock, never()).addAttachmentToDraft(any(), any(), any());
		verify(supportManagementIntegrationMock, never()).getAttachment(any(), any(), any(), any());
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenAnAttachmentCannotBeFetched() {
		givenADraftFor(approval().attachments(List.of(new ErrandAttachment().id("first"), new ErrandAttachment().id("second"))));
		when(supportManagementIntegrationMock.getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "first"))
			.thenThrow(Problem.valueOf(BAD_GATEWAY, "Support management is down"));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
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

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");

		verify(supportManagementIntegrationMock, never()).getAttachment(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "second");
		verify(partyAssetsIntegrationMock, never()).activateAsset(any(), any());
		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetRemovesTheDraftWhenTheActivationFails() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down");

		verify(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void findOrCreateAssetCarriesTheAssetIdWhenTheDraftCannotBeRemoved() {
		givenADraftFor(approval());
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down")).when(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
		doThrow(new ClientProblem(BAD_GATEWAY, "Party assets is still down")).when(partyAssetsIntegrationMock).removeDraftAsset(MUNICIPALITY_ID, ASSET_ID);

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(ASSET_ID)
			.hasMessageContaining("ClientProblem 502")
			.hasMessageNotContaining("Party assets is down")
			.hasMessageNotContaining("Party assets is still down");
	}

	@Test
	void findOrCreateAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	/** An approval with conditions grants the permit as well, so it is created the same way. */
	@Test
	void findOrCreateAssetCreatesThePermitOfAnApprovalWithConditions() {
		givenADraftFor(new Decision().id(DECISION_ID).outcome("APPROVAL_WITH_CONDITIONS"));

		assertThat(assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).activateAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void findOrCreateAssetFailsOnAnOutcomeThatGrantsNoPermit(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome(outcome)));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created or changed from one of [APPROVAL, APPROVAL_WITH_CONDITIONS]");

		verifyNoInteractions(partyAssetsIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsOnADecisionWithoutId() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval().id(null)));

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no id");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void findOrCreateAssetFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> assetService.findOrCreateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, null))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(STAKEHOLDER_ROLE_PERMIT_HOLDER);

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	@Test
	void updateAssetRendersTheCertificateBeforeItPatchesTheAssetAndReplacesTheCertificate() throws IOException {
		final var pdf = "%PDF-1.7".getBytes();
		final var asset = activeAsset().additionalParameters(Map.of(
			"permitHolderName", "Runt Hörnet AB",
			"serveringstid", "11.00–01.00",
			"conditions", "Ordningsvakt efter 23.00."));

		givenAnErrandNaming(approval()
			.validTo(LocalDate.of(2027, 9, 30))
			.parameters(List.of(new Parameter().key("serveringstid").values(List.of("11.00–02.00"))))
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Ordningsvakt efter 01.00."))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(asset));
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any())).thenReturn(pdf);

		assertThat(assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE)).isEqualTo(ASSET_ID);

		final InOrder inOrder = inOrder(partyAssetsIntegrationMock, templatingIntegrationMock);
		inOrder.verify(templatingIntegrationMock).renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		inOrder.verify(partyAssetsIntegrationMock).replaceCertificate(eq(MUNICIPALITY_ID), eq(ASSET_ID), attachmentCaptor.capture());

		assertThat(updateCaptor.getValue().getValidTo()).isEqualTo(LocalDate.of(2027, 9, 30));
		assertThat(updateCaptor.getValue().getAdditionalParameters())
			.containsEntry("permitHolderName", "Runt Hörnet AB")
			.containsEntry("serveringstid", "11.00–02.00")
			.containsEntry("conditions", "Ordningsvakt efter 01.00.")
			.containsEntry("errandId", ERRAND_ID);
		assertThat(templateParametersCaptor.getValue())
			.containsEntry("permitHolderName", "Runt Hörnet AB")
			.containsEntry("serveringstid", "11.00–02.00")
			.containsEntry("conditions", "Ordningsvakt efter 01.00.");
		assertThat(attachmentCaptor.getValue().file().getBytes()).isEqualTo(pdf);
		assertThat(attachmentCaptor.getValue().category()).isEqualTo("Tillståndsbevis");
		verify(partyAssetsIntegrationMock, never()).createDraftAsset(any(), any(), any(), any());
	}

	/** A change that sets no conditions leaves those of the permit in place, on the asset and on its certificate. */
	@Test
	void updateAssetKeepsTheConditionsOfThePermitWhenTheChangeSetsNone() {
		final var asset = activeAsset().additionalParameters(Map.of("conditions", "Ordningsvakt efter 23.00."));

		givenAnErrandNaming(approval().parameters(List.of(new Parameter().key("serveringstid").values(List.of("11.00–02.00")))));
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(asset));
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getAdditionalParameters()).containsEntry("conditions", "Ordningsvakt efter 23.00.");
		assertThat(templateParametersCaptor.getValue()).containsEntry("conditions", "Ordningsvakt efter 23.00.");
	}

	/**
	 * The template is strict, so a placeholder still needs a value when neither the permit nor the change has conditions.
	 */
	@Test
	void updateAssetRendersEmptyConditionsWhenThePermitHasNone() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), templateParametersCaptor.capture())).thenReturn("%PDF-1.7".getBytes());

		assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE);

		assertThat(templateParametersCaptor.getValue()).containsEntry("conditions", "");
	}

	@Test
	void updateAssetLeavesTheAssetAloneWhenTheCertificateCannotBeRendered() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));
		when(templatingIntegrationMock.renderPdf(eq(MUNICIPALITY_ID), eq(CERTIFICATE_TEMPLATE), any()))
			.thenThrow(new NonRetryableException("Missing template parameter 'premisesName'"));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Missing template parameter 'premisesName'");

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verify(partyAssetsIntegrationMock, never()).replaceCertificate(any(), any(), any());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"", " "
	})
	void updateAssetLeavesTheCertificateAloneWithoutATemplate(final String certificateTemplate) {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset()));

		assertThat(assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, certificateTemplate)).isEqualTo(ASSET_ID);

		verify(partyAssetsIntegrationMock).updateAsset(eq(MUNICIPALITY_ID), eq(ASSET_ID), eq(VERSION), any());
		verify(partyAssetsIntegrationMock, never()).replaceCertificate(any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"", " "
	})
	void updateAssetFailsWithoutRetryWhenTheErrandNamesNoAsset(final String assetId) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(assetId == null ? null : List.of(assetId)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names no asset to change");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"asset-id", "9c8b7a6d", "9c8b7a6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d-extra"
	})
	void updateAssetFailsWithoutRetryWhenTheErrandNamesSomethingThatIsNotAnAssetId(final String assetId) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(new Parameter().key("assetId").values(List.of(assetId)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Errand 'errand-id' names asset '%s', which is not an asset id".formatted(assetId));

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutRetryWhenTheErrandHasNoParameters() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithPermitHolder().parameters(null));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("names no asset to change");
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = "ACTIVE", mode = EXCLUDE)
	void updateAssetFailsWithoutRetryOnAnAssetThatIsNotActive(final Status status) {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().status(status)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' has status %s, only an active permit can be changed".formatted(ASSET_ID, status));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
		verifyNoInteractions(templatingIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutRetryOnAnAssetOfAnotherParty() {
		givenAnErrandNaming(approval());
		when(partyAssetsIntegrationMock.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(versioned(activeAsset().partyId("someone-else")));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset '%s' does not belong to the permit holder of errand 'errand-id'".formatted(ASSET_ID));

		verify(partyAssetsIntegrationMock, never()).updateAsset(any(), any(), any(), any());
	}

	@Test
	void updateAssetFailsWithoutAPermitHolder() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(approval()));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(new Errand().parameters(List.of(new Parameter().key("assetId").values(List.of(ASSET_ID)))));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(STAKEHOLDER_ROLE_PERMIT_HOLDER);

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
		"REJECTED", "DISMISSED", "INADMISSIBLE"
	})
	void updateAssetFailsOnAnOutcomeThatGrantsNoPermit(final String outcome) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(Optional.of(new Decision().id(DECISION_ID).outcome(outcome)));

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("only created or changed from one of [APPROVAL, APPROVAL_WITH_CONDITIONS]");

		verifyNoInteractions(partyAssetsIntegrationMock, templatingIntegrationMock);
		verifyNoMoreInteractions(supportManagementIntegrationMock);
	}

	@Test
	void updateAssetFailsWithoutACompletedDecision() {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assetService.updateAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CERTIFICATE_TEMPLATE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("no completed decision");

		verifyNoInteractions(partyAssetsIntegrationMock);
	}

	private void givenAnErrandNaming(final Decision decision) {
		when(supportManagementIntegrationMock.getCompletedDecision(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(Optional.of(decision));
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID))
			.thenReturn(errandWithPermitHolder().parameters(List.of(
				new Parameter().key("caseNumber").values(List.of("IAN-2026-00209")),
				new Parameter().key("assetId").values(List.of(" ", ASSET_ID)))));
	}

	private static VersionedAsset versioned(final Asset asset) {
		return new VersionedAsset(asset, VERSION);
	}

	private static Asset activeAsset() {
		return new Asset().id(ASSET_ID).partyId(PARTY_ID).status(Status.ACTIVE);
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
		return new Errand().stakeholders(List.of(new Stakeholder().role("APPLICANT").externalId("applicant-id"), new Stakeholder().role("PRIMARY").externalId(PARTY_ID)));
	}
}
