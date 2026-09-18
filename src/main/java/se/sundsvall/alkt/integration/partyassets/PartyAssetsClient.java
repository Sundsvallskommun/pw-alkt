package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static se.sundsvall.alkt.integration.partyassets.configuration.PartyAssetsConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.party-assets.url}", configuration = PartyAssetsConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface PartyAssetsClient {

	@PostMapping(path = "/{municipalityId}/assets", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAsset(
		@PathVariable String municipalityId,
		@RequestBody AssetCreateRequest asset);

	@PostMapping(path = "/{municipalityId}/assets/{assetId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAttachment(
		@PathVariable String municipalityId,
		@PathVariable String assetId,
		@RequestPart("attachment") MultipartFile attachment,
		@RequestPart(name = "category", required = false) String category,
		@RequestPart(name = "description", required = false) String description);
}
