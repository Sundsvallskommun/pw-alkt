package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsProperties;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class PartyAssetsIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String PARTY_ID = "party-id";
	private static final String DECISION_ID = "decision-id";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String SOURCE_REFERENCE = "LINK|errand-id;case;supportmanagement;ALKT|";

	@Mock
	private PartyAssetsClient partyAssetsClientMock;

	private PartyAssetsIntegration partyAssetsIntegration;

	@BeforeEach
	void setUp() {
		partyAssetsIntegration = new PartyAssetsIntegration(partyAssetsClientMock, new PartyAssetsProperties(5, 20, "LINK"));
		lenient().when(partyAssetsClientMock.getDraftAssets(any(), any(), any())).thenReturn(ResponseEntity.ok(List.of()));
	}

	@Test
	void findAssetIdAnswersWithTheIdOfTheMatchingAsset() {
		when(partyAssetsClientMock.getAssets(MUNICIPALITY_ID, "party-id", "decision-id")).thenReturn(ResponseEntity.ok(List.of(new Asset().id("asset-id"))));

		assertThat(partyAssetsIntegration.findAssetId(MUNICIPALITY_ID, "party-id", "decision-id")).contains("asset-id");
	}

	@Test
	void findAssetIdIsEmptyWithoutABody() {
		when(partyAssetsClientMock.getAssets(MUNICIPALITY_ID, "party-id", "decision-id")).thenReturn(ResponseEntity.ok(null));

		assertThat(partyAssetsIntegration.findAssetId(MUNICIPALITY_ID, "party-id", "decision-id")).isEmpty();
	}

	@Test
	void createDraftAssetAnswersWithTheIdOfTheDraft() {
		final var assetId = randomUUID().toString();
		final var asset = asset();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, SOURCE_REFERENCE, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));

		assertThat(partyAssetsIntegration.createDraftAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, asset)).isEqualTo(assetId);

		verify(partyAssetsClientMock).getDraftAssets(MUNICIPALITY_ID, PARTY_ID, DECISION_ID);
		verify(partyAssetsClientMock).createDraftAsset(MUNICIPALITY_ID, SOURCE_REFERENCE, asset);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void createDraftAssetRemovesADraftLeftBehindByAnEarlierAttempt() {
		final var assetId = randomUUID().toString();
		final var asset = asset();

		when(partyAssetsClientMock.getDraftAssets(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(ResponseEntity.ok(List.of(new Asset().id("left-behind"))));
		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, SOURCE_REFERENCE, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));

		assertThat(partyAssetsIntegration.createDraftAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, asset)).isEqualTo(assetId);

		final InOrder inOrder = inOrder(partyAssetsClientMock);
		inOrder.verify(partyAssetsClientMock).deleteAsset(MUNICIPALITY_ID, "left-behind");
		inOrder.verify(partyAssetsClientMock).createDraftAsset(MUNICIPALITY_ID, SOURCE_REFERENCE, asset);
	}

	@Test
	void createDraftAssetCreatesNothingWhenALeftoverDraftCannotBeRemoved() {
		when(partyAssetsClientMock.getDraftAssets(MUNICIPALITY_ID, PARTY_ID, DECISION_ID)).thenReturn(ResponseEntity.ok(List.of(new Asset().id("left-behind"))));
		when(partyAssetsClientMock.deleteAsset(MUNICIPALITY_ID, "left-behind")).thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down"));

		assertThatThrownBy(() -> partyAssetsIntegration.createDraftAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, asset()))
			.isInstanceOf(ClientProblem.class);

		verify(partyAssetsClientMock, never()).createDraftAsset(any(), any(), any());
	}

	@Test
	void createDraftAssetFailsWhenTheAnswerCarriesNoLocation() {
		final var asset = asset();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, SOURCE_REFERENCE, asset)).thenReturn(ResponseEntity.status(CREATED).build());

		assertThatThrownBy(() -> partyAssetsIntegration.createDraftAsset(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, asset))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	@Test
	void addAttachmentToDraftUploadsTheFileWithItsCategory() {
		final var file = mock(MultipartFile.class);

		partyAssetsIntegration.addAttachmentToDraft(MUNICIPALITY_ID, "asset-id", new AssetFile(file, "LOKALRITNING"));

		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, "asset-id", file, "LOKALRITNING", null, null);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void activateAssetSetsTheDraftActive() {
		partyAssetsIntegration.activateAsset(MUNICIPALITY_ID, "asset-id");

		verify(partyAssetsClientMock).updateDraftAsset(MUNICIPALITY_ID, "asset-id", new DraftAssetUpdateRequest().status(ACTIVE));
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void removeDraftAssetDeletesTheDraft() {
		partyAssetsIntegration.removeDraftAsset(MUNICIPALITY_ID, "asset-id");

		verify(partyAssetsClientMock).deleteAsset(MUNICIPALITY_ID, "asset-id");
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void getAssetAnswersWithTheAssetAndItsVersion() {
		final var asset = new Asset().id("asset-id");
		when(partyAssetsClientMock.getAsset(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok().eTag("\"3\"").body(asset));

		assertThat(partyAssetsIntegration.getAsset(MUNICIPALITY_ID, "asset-id")).isEqualTo(new VersionedAsset(asset, "\"3\""));
	}

	@Test
	void getAssetHasNoVersionWithoutAnETag() {
		final var asset = new Asset().id("asset-id");
		when(partyAssetsClientMock.getAsset(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok(asset));

		assertThat(partyAssetsIntegration.getAsset(MUNICIPALITY_ID, "asset-id")).isEqualTo(new VersionedAsset(asset, null));
	}

	@Test
	void getAssetFailsWithoutRetryWhenTheIdNamesNoAsset() {
		when(partyAssetsClientMock.getAsset(MUNICIPALITY_ID, "asset-id")).thenThrow(new ClientProblem(NOT_FOUND, "No asset"));

		assertThatThrownBy(() -> partyAssetsIntegration.getAsset(MUNICIPALITY_ID, "asset-id"))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining("Asset 'asset-id' cannot be read")
			.hasCauseInstanceOf(ClientProblem.class);
	}

	@Test
	void getAssetLeavesAnyOtherFailureToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "Party assets is down");
		when(partyAssetsClientMock.getAsset(MUNICIPALITY_ID, "asset-id")).thenThrow(problem);

		assertThatThrownBy(() -> partyAssetsIntegration.getAsset(MUNICIPALITY_ID, "asset-id")).isSameAs(problem);
	}

	@Test
	void getAssetFailsWithoutABody() {
		when(partyAssetsClientMock.getAsset(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> partyAssetsIntegration.getAsset(MUNICIPALITY_ID, "asset-id"))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Asset 'asset-id' came back without content");
	}

	@Test
	void updateAssetPatchesTheVersionThatWasRead() {
		final var request = new AssetUpdateRequest().additionalParameters(Map.of("key", "value"));

		partyAssetsIntegration.updateAsset(MUNICIPALITY_ID, "asset-id", "\"3\"", request);

		verify(partyAssetsClientMock).updateAsset(MUNICIPALITY_ID, "asset-id", "\"3\"", request);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void replaceCertificateReplacesTheCertificateTheAssetHas() {
		final var file = mock(MultipartFile.class);

		when(partyAssetsClientMock.getAttachments(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok(List.of(
			new AssetAttachment().id("drawing").category("LOKALRITNING"),
			new AssetAttachment().id("uncategorised"),
			new AssetAttachment().id("old-certificate").category("Tillståndsbevis"))));

		partyAssetsIntegration.replaceCertificate(MUNICIPALITY_ID, "asset-id", new AssetFile(file, "Tillståndsbevis"));

		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, "asset-id", file, "Tillståndsbevis", null, "old-certificate");
	}

	@Test
	void replaceCertificateAddsTheCertificateToAnAssetWithoutOne() {
		final var file = mock(MultipartFile.class);

		when(partyAssetsClientMock.getAttachments(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok(List.of(new AssetAttachment().id("drawing").category("LOKALRITNING"))));

		partyAssetsIntegration.replaceCertificate(MUNICIPALITY_ID, "asset-id", new AssetFile(file, "Tillståndsbevis"));

		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, "asset-id", file, "Tillståndsbevis", null, null);
	}

	@Test
	void replaceCertificateAddsTheCertificateWhenTheAttachmentsComeBackWithoutBody() {
		final var file = mock(MultipartFile.class);

		when(partyAssetsClientMock.getAttachments(MUNICIPALITY_ID, "asset-id")).thenReturn(ResponseEntity.ok(null));

		partyAssetsIntegration.replaceCertificate(MUNICIPALITY_ID, "asset-id", new AssetFile(file, "Tillståndsbevis"));

		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, "asset-id", file, "Tillståndsbevis", null, null);
	}

	private static AssetCreateRequest asset() {
		return new AssetCreateRequest().partyId(PARTY_ID).assetId(DECISION_ID);
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
