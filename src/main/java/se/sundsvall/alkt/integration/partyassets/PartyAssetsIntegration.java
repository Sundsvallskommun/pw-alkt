package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class PartyAssetsIntegration {

	private static final String SERVICE = "Party assets";

	private final PartyAssetsClient partyAssetsClient;

	PartyAssetsIntegration(final PartyAssetsClient partyAssetsClient) {
		this.partyAssetsClient = partyAssetsClient;
	}

	public Optional<String> findAssetId(final String municipalityId, final String assetId) {
		return Optional.ofNullable(partyAssetsClient.getAssets(municipalityId, assetId).getBody())
			.orElseGet(List::of)
			.stream()
			.map(Asset::getId)
			.findFirst();
	}

	public String createAsset(final String municipalityId, final AssetCreateRequest asset, final List<AssetFile> attachments) {
		final var files = Optional.ofNullable(attachments).orElseGet(List::of);
		final var assetId = getIdOfCreatedResource(partyAssetsClient.createDraftAsset(municipalityId, asset.status(DRAFT)), SERVICE);

		try {
			files.forEach(attachment -> partyAssetsClient.createAttachment(municipalityId, assetId, attachment.file(), attachment.category(), null));
			partyAssetsClient.updateDraftAsset(municipalityId, assetId, new DraftAssetUpdateRequest().status(ACTIVE));
		} catch (final RuntimeException e) {
			throw Problem.valueOf(BAD_GATEWAY, removeAsset(municipalityId, assetId, e));
		}

		return assetId;
	}

	private String removeAsset(final String municipalityId, final String assetId, final RuntimeException cause) {
		try {
			partyAssetsClient.deleteAsset(municipalityId, assetId);
			return "The draft asset could not be completed and was removed again: %s".formatted(cause.getMessage());
		} catch (final RuntimeException e) {
			// The id is the only way to find the asset that is left behind, so it travels with both failures.
			return "Draft asset '%s' could not be completed (%s) and could not be removed again: %s".formatted(assetId, cause.getMessage(), e.getMessage());
		}
	}
}
