package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsProperties;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toSourceReference;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class PartyAssetsIntegration {

	private static final String SERVICE = "Party assets";

	private final PartyAssetsClient partyAssetsClient;
	private final PartyAssetsProperties properties;

	PartyAssetsIntegration(final PartyAssetsClient partyAssetsClient, final PartyAssetsProperties properties) {
		this.partyAssetsClient = partyAssetsClient;
		this.properties = properties;
	}

	public Optional<String> findAssetId(final String municipalityId, final String partyId, final String assetId) {
		return idsOf(partyAssetsClient.getAssets(municipalityId, partyId, assetId).getBody()).findFirst();
	}

	public String createDraftAsset(final String municipalityId, final String namespace, final String errandId, final AssetCreateRequest asset) {
		idsOf(partyAssetsClient.getDraftAssets(municipalityId, asset.getPartyId(), asset.getAssetId()).getBody())
			.forEach(draftId -> partyAssetsClient.deleteAsset(municipalityId, draftId));

		return getIdOfCreatedResource(partyAssetsClient.createDraftAsset(municipalityId, toSourceReference(properties.relationType(), errandId, namespace), asset), SERVICE);
	}

	public void addAttachmentToDraft(final String municipalityId, final String assetId, final AssetFile attachment) {
		partyAssetsClient.createAttachment(municipalityId, assetId, attachment.file(), attachment.category(), null);
	}

	public void activateAsset(final String municipalityId, final String assetId) {
		partyAssetsClient.updateDraftAsset(municipalityId, assetId, new DraftAssetUpdateRequest().status(ACTIVE));
	}

	public void removeDraftAsset(final String municipalityId, final String assetId) {
		partyAssetsClient.deleteAsset(municipalityId, assetId);
	}

	private static Stream<String> idsOf(final List<Asset> assets) {
		return Optional.ofNullable(assets).orElse(emptyList()).stream().map(Asset::getId);
	}
}
