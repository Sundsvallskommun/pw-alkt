package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CREATED;

@ExtendWith(MockitoExtension.class)
class PartyAssetsIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final DraftAssetUpdateRequest ACTIVATION = new DraftAssetUpdateRequest().status(ACTIVE);

	@Mock
	private PartyAssetsClient partyAssetsClientMock;

	@InjectMocks
	private PartyAssetsIntegration partyAssetsIntegration;

	@Test
	void createAssetCreatesADraftAttachesEverythingAndThenActivates() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();
		final var firstFile = mock(MultipartFile.class);
		final var secondFile = mock(MultipartFile.class);
		final var attachments = List.of(new AssetFile(firstFile, "LOKALRITNING"), new AssetFile(secondFile, null));

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));

		final var result = partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, attachments);

		assertThat(result).isEqualTo(assetId);
		assertThat(asset.getStatus()).isEqualTo(DRAFT);
		final InOrder inOrder = inOrder(partyAssetsClientMock);
		inOrder.verify(partyAssetsClientMock).createDraftAsset(MUNICIPALITY_ID, asset);
		inOrder.verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, assetId, firstFile, "LOKALRITNING", null);
		inOrder.verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, assetId, secondFile, null, null);
		inOrder.verify(partyAssetsClientMock).updateDraftAsset(MUNICIPALITY_ID, assetId, ACTIVATION);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void findAssetIdAnswersWithTheIdOfTheMatchingAsset() {
		when(partyAssetsClientMock.getAssets(MUNICIPALITY_ID, "decision-id")).thenReturn(ResponseEntity.ok(List.of(new Asset().id("asset-id"))));

		assertThat(partyAssetsIntegration.findAssetId(MUNICIPALITY_ID, "decision-id")).contains("asset-id");
	}

	@Test
	void findAssetIdIsEmptyWithoutABody() {
		when(partyAssetsClientMock.getAssets(MUNICIPALITY_ID, "decision-id")).thenReturn(ResponseEntity.ok(null));

		assertThat(partyAssetsIntegration.findAssetId(MUNICIPALITY_ID, "decision-id")).isEmpty();
	}

	@Test
	void createAssetWithoutAttachmentsCreatesAndActivatesTheDraft() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));

		final var result = partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of());

		assertThat(result).isEqualTo(assetId);
		verify(partyAssetsClientMock).createDraftAsset(MUNICIPALITY_ID, asset);
		verify(partyAssetsClientMock).updateDraftAsset(MUNICIPALITY_ID, assetId, ACTIVATION);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void createAssetWithNullAttachmentsCreatesAndActivatesTheDraft() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));

		final var result = partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, null);

		assertThat(result).isEqualTo(assetId);
		verify(partyAssetsClientMock).createDraftAsset(MUNICIPALITY_ID, asset);
		verify(partyAssetsClientMock).updateDraftAsset(MUNICIPALITY_ID, assetId, ACTIVATION);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void createAssetFailsWhenTheAnswerCarriesNoLocation() {
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(ResponseEntity.status(CREATED).build());

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(new AssetFile(mock(MultipartFile.class), null))))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");

		verify(partyAssetsClientMock, never()).createAttachment(any(), any(), any(), any(), any());
		verify(partyAssetsClientMock, never()).updateDraftAsset(any(), any(), any());
	}

	@Test
	void createAssetRemovesTheDraftWhenAnAttachmentFails() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();
		final var firstFile = mock(MultipartFile.class);
		final var secondFile = mock(MultipartFile.class);

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));
		when(partyAssetsClientMock.createAttachment(MUNICIPALITY_ID, assetId, firstFile, null, null))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down"));

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(new AssetFile(firstFile, null), new AssetFile(secondFile, null))))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Party assets is down");

		verify(partyAssetsClientMock, never()).createAttachment(MUNICIPALITY_ID, assetId, secondFile, null, null);
		verify(partyAssetsClientMock, never()).updateDraftAsset(any(), any(), any());
		verify(partyAssetsClientMock).deleteAsset(MUNICIPALITY_ID, assetId);
	}

	@Test
	void createAssetRemovesTheDraftWhenTheActivationFails() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));
		when(partyAssetsClientMock.updateDraftAsset(MUNICIPALITY_ID, assetId, ACTIVATION))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down"));

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of()))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("removed again")
			.hasMessageContaining("Party assets is down");

		verify(partyAssetsClientMock).deleteAsset(MUNICIPALITY_ID, assetId);
	}

	@Test
	void createAssetCarriesTheAssetIdWhenTheDraftCannotBeRemoved() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();
		final var file = mock(MultipartFile.class);

		when(partyAssetsClientMock.createDraftAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/asset-drafts/" + assetId));
		when(partyAssetsClientMock.createAttachment(MUNICIPALITY_ID, assetId, file, null, null))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down"));
		when(partyAssetsClientMock.deleteAsset(MUNICIPALITY_ID, assetId))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is still down"));

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(new AssetFile(file, null))))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(assetId)
			.hasMessageContaining("Party assets is down")
			.hasMessageContaining("Party assets is still down");
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
