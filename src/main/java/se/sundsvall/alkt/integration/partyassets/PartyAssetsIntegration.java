package se.sundsvall.alkt.integration.partyassets;

import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class PartyAssetsIntegration {

	private final PartyAssetsClient partyAssetsClient;

	PartyAssetsIntegration(final PartyAssetsClient partyAssetsClient) {
		this.partyAssetsClient = partyAssetsClient;
	}

	// TODO: create the asset from the request the mapper builds, read its id out of the Location header with assetIdOf,
	// then post each attachment to /assets/{assetId}/attachments and answer with the id. An attachment that fails leaves
	// the asset created, party-assets has no way to take it back. Arguments follow once the worker knows what it reads
	// out of the errand.
	public void createAsset() {}

	// Location points at GET /assets/{id}, so its last segment is the id. No body comes back to read it from.
	static String assetIdOf(final ResponseEntity<Void> response) {
		return Optional.ofNullable(response)
			.map(ResponseEntity::getHeaders)
			.map(HttpHeaders::getLocation)
			.map(URI::getPath)
			.map(path -> substringAfterLast(path, "/"))
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Party assets created an asset without saying which"));
	}
}
