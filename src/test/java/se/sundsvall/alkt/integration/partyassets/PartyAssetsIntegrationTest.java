package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	@Mock
	private PartyAssetsClient partyAssetsClientMock;

	@InjectMocks
	private PartyAssetsIntegration partyAssetsIntegration;

	@Test
	void createAssetHangsEveryAttachmentOnTheCreatedAsset() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();
		final var firstAttachment = mock(MultipartFile.class);
		final var secondAttachment = mock(MultipartFile.class);

		when(partyAssetsClientMock.createAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/assets/" + assetId));

		final var result = partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(firstAttachment, secondAttachment));

		assertThat(result).isEqualTo(assetId);
		verify(partyAssetsClientMock).createAsset(MUNICIPALITY_ID, asset);
		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, assetId, firstAttachment, null, null);
		verify(partyAssetsClientMock).createAttachment(MUNICIPALITY_ID, assetId, secondAttachment, null, null);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void createAssetWithoutAttachmentsOnlyCreatesTheAsset() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/assets/" + assetId));

		final var result = partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of());

		assertThat(result).isEqualTo(assetId);
		verify(partyAssetsClientMock).createAsset(MUNICIPALITY_ID, asset);
		verifyNoMoreInteractions(partyAssetsClientMock);
	}

	@Test
	void createAssetFailsWhenTheAnswerCarriesNoLocation() {
		final var asset = new AssetCreateRequest();

		when(partyAssetsClientMock.createAsset(MUNICIPALITY_ID, asset)).thenReturn(ResponseEntity.status(CREATED).build());

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(mock(MultipartFile.class))))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");

		verify(partyAssetsClientMock, never()).createAttachment(any(), any(), any(), any(), any());
	}

	/**
	 * A failing attachment leaves the asset behind, so its id is carried by the fault and the next one is not attempted.
	 */
	@Test
	void createAssetPassesOnAFailingAttachment() {
		final var assetId = randomUUID().toString();
		final var asset = new AssetCreateRequest();
		final var firstAttachment = mock(MultipartFile.class);
		final var secondAttachment = mock(MultipartFile.class);

		when(partyAssetsClientMock.createAsset(MUNICIPALITY_ID, asset)).thenReturn(created("https://party-assets.example.com/2281/assets/" + assetId));
		when(partyAssetsClientMock.createAttachment(MUNICIPALITY_ID, assetId, firstAttachment, null, null))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Party assets is down"));

		assertThatThrownBy(() -> partyAssetsIntegration.createAsset(MUNICIPALITY_ID, asset, List.of(firstAttachment, secondAttachment)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining(assetId)
			.hasMessageContaining("Party assets is down");

		verify(partyAssetsClientMock, never()).createAttachment(MUNICIPALITY_ID, assetId, secondAttachment, null, null);
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
