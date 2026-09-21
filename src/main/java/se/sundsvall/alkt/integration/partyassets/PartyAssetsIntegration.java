package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.List;
import java.util.Optional;
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
		final var files = Optional.ofNullable(attachments).orElseGet(List::of);
		final var assetId = getIdOfCreatedResource(partyAssetsClient.createAsset(municipalityId, asset), SERVICE);

		// Category and description are null until the worker knows what the attachments of an errand are called.
		try {
			files.forEach(attachment -> partyAssetsClient.createAttachment(municipalityId, assetId, attachment, null, null));
		} catch (final RuntimeException e) {
			// Everything is caught, not just Problem: an open circuit breaker and a read timeout are the likeliest ways an
			// attachment fails. The asset goes with it, so an engine retry of the step does not leave a duplicate behind.
			throw Problem.valueOf(BAD_GATEWAY, removeAsset(municipalityId, assetId, e));
		}

		return assetId;
	}

	private String removeAsset(final String municipalityId, final String assetId, final RuntimeException cause) {
		try {
			partyAssetsClient.deleteAsset(municipalityId, assetId);
			return "An attachment could not be added to the created asset, which was removed again: %s".formatted(cause.getMessage());
		} catch (final RuntimeException e) {
			// The id is the only way to find the asset that is left behind, so it travels with both failures.
			return "An attachment could not be added to asset '%s' (%s) and the asset could not be removed again: %s".formatted(assetId, cause.getMessage(), e.getMessage());
		}
	}
}
