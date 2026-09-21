package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class PartyAssetsIntegration {

	private static final String SERVICE = "Party assets";

	private final PartyAssetsClient partyAssetsClient;

	PartyAssetsIntegration(final PartyAssetsClient partyAssetsClient) {
		this.partyAssetsClient = partyAssetsClient;
	}

	public String createAsset(final String municipalityId, final AssetCreateRequest asset, final List<MultipartFile> attachments) {
		final var assetId = getIdOfCreatedResource(partyAssetsClient.createAsset(municipalityId, asset), SERVICE);

		// Category and description are null until the worker knows what the attachments of an errand are called.
		try {
			attachments.forEach(attachment -> partyAssetsClient.createAttachment(municipalityId, assetId, attachment, null, null));
		} catch (final RuntimeException e) {
			// The asset is created and party-assets cannot take it back, so its id travels with the failure. A rerun that
			// creates the asset again leaves a duplicate no one can remove. Everything is caught, not just Problem: an
			// open circuit breaker and a read timeout are the likeliest ways an attachment fails.
			throw Problem.valueOf(BAD_GATEWAY, "Asset '%s' was created but an attachment could not be added to it: %s".formatted(assetId, e.getMessage()));
		}

		return assetId;
	}
}
