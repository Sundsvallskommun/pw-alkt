package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsProperties;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandRelation;
import static se.sundsvall.alkt.util.RemoteAnswers.failWithoutRetryOn;
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

	public Optional<Asset> findAsset(final String municipalityId, final String partyId, final String assetId) {
		return Optional.ofNullable(partyAssetsClient.getAssets(municipalityId, partyId, assetId).getBody()).orElse(emptyList()).stream().findFirst();
	}

	/** The id comes from the customer, so one that names no asset is not something a retry fixes. */
	public VersionedAsset getAsset(final String municipalityId, final String id) {
		final var response = failWithoutRetryOn(NOT_FOUND, "Asset '%s' cannot be read".formatted(id), () -> partyAssetsClient.getAsset(municipalityId, id));
		final var asset = Optional.ofNullable(response.getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Asset '%s' came back without content".formatted(id)));
		return new VersionedAsset(asset, response.getHeaders().getETag());
	}

	/**
	 * A version that no longer matches is answered with 412, and the step reruns on a fresh read. A 400 is a change
	 * party-assets refuses, which a retry does not change.
	 */
	public void updateAsset(final String municipalityId, final String id, final String version, final AssetUpdateRequest asset) {
		failWithoutRetryOn(BAD_REQUEST, "Asset '%s' cannot be updated".formatted(id), () -> partyAssetsClient.updateAsset(municipalityId, id, version, asset));
	}

	/**
	 * Replaces the attachment in the category of the certificate, which party-assets keeps in the revision history. An
	 * asset without one simply gets the certificate.
	 */
	public void replaceCertificate(final String municipalityId, final String assetId, final AssetFile certificate) {
		final var replaces = Optional.ofNullable(partyAssetsClient.getAttachments(municipalityId, assetId).getBody()).orElse(emptyList()).stream()
			.filter(attachment -> certificate.category().equals(attachment.getCategory()))
			.map(AssetAttachment::getId)
			.findFirst()
			.orElse(null);

		partyAssetsClient.createAttachment(municipalityId, assetId, certificate.file(), certificate.category(), null, replaces);
	}

	/**
	 * A 400 is a draft party-assets refuses, for instance a JSON parameter that does not follow its schema. No draft is
	 * created then, and a retry does not change it.
	 */
	public String createDraftAsset(final String municipalityId, final String namespace, final String errandId, final AssetCreateRequest asset) {
		idsOf(partyAssetsClient.getDraftAssets(municipalityId, asset.getPartyId(), asset.getAssetId()).getBody())
			.forEach(draftId -> partyAssetsClient.deleteAsset(municipalityId, draftId));

		return getIdOfCreatedResource(failWithoutRetryOn(BAD_REQUEST, "The draft asset of errand '%s' cannot be created".formatted(errandId),
			() -> partyAssetsClient.createDraftAsset(municipalityId, toErrandRelation(properties.relationType(), errandId, namespace), asset)), SERVICE);
	}

	public void addAttachmentToDraft(final String municipalityId, final String assetId, final AssetFile attachment) {
		partyAssetsClient.createAttachment(municipalityId, assetId, attachment.file(), attachment.category(), null, null);
	}

	/** party-assets refuses to activate a permit whose validTo has passed, which a retry does not change. */
	public void activateAsset(final String municipalityId, final String assetId) {
		failWithoutRetryOn(BAD_REQUEST, "Asset '%s' cannot be activated".formatted(assetId), () -> partyAssetsClient.updateDraftAsset(municipalityId, assetId, new DraftAssetUpdateRequest().status(ACTIVE)));
	}

	public void removeDraftAsset(final String municipalityId, final String assetId) {
		partyAssetsClient.deleteAsset(municipalityId, assetId);
	}

	private static Stream<String> idsOf(final List<Asset> assets) {
		return Optional.ofNullable(assets).orElse(emptyList()).stream().map(Asset::getId);
	}
}
