package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsProperties;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toSourceReference;
import static se.sundsvall.alkt.util.FailureDescription.describe;
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

	/** The id comes from the customer, so one that names no asset is not something a retry fixes. */
	public VersionedAsset getAsset(final String municipalityId, final String id) {
		final ResponseEntity<Asset> response;
		try {
			response = partyAssetsClient.getAsset(municipalityId, id);
		} catch (final ClientProblem e) {
			if (NOT_FOUND.equals(e.getStatus())) {
				throw new NonRetryableException("Asset '%s' cannot be read: %s".formatted(id, describe(e)), e);
			}
			throw e;
		}
		final var asset = Optional.ofNullable(response.getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Asset '%s' came back without content".formatted(id)));
		return new VersionedAsset(asset, response.getHeaders().getETag());
	}

	/** A version that no longer matches is answered with 412, and the step reruns on a fresh read. */
	public void updateAsset(final String municipalityId, final String id, final String version, final AssetUpdateRequest asset) {
		partyAssetsClient.updateAsset(municipalityId, id, version, asset);
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

	public String createDraftAsset(final String municipalityId, final String namespace, final String errandId, final AssetCreateRequest asset) {
		idsOf(partyAssetsClient.getDraftAssets(municipalityId, asset.getPartyId(), asset.getAssetId()).getBody())
			.forEach(draftId -> partyAssetsClient.deleteAsset(municipalityId, draftId));

		return getIdOfCreatedResource(partyAssetsClient.createDraftAsset(municipalityId, toSourceReference(properties.relationType(), errandId, namespace), asset), SERVICE);
	}

	public void addAttachmentToDraft(final String municipalityId, final String assetId, final AssetFile attachment) {
		partyAssetsClient.createAttachment(municipalityId, assetId, attachment.file(), attachment.category(), null, null);
	}

	/** party-assets refuses to activate a permit whose validTo has passed, which a retry does not change. */
	public void activateAsset(final String municipalityId, final String assetId) {
		try {
			partyAssetsClient.updateDraftAsset(municipalityId, assetId, new DraftAssetUpdateRequest().status(ACTIVE));
		} catch (final ClientProblem e) {
			if (BAD_REQUEST.equals(e.getStatus())) {
				throw new NonRetryableException("Asset '%s' cannot be activated: %s".formatted(assetId, describe(e)), e);
			}
			throw e;
		}
	}

	public void removeDraftAsset(final String municipalityId, final String assetId) {
		partyAssetsClient.deleteAsset(municipalityId, assetId);
	}

	private static Stream<String> idsOf(final List<Asset> assets) {
		return Optional.ofNullable(assets).orElse(emptyList()).stream().map(Asset::getId);
	}
}
