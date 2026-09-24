package se.sundsvall.alkt.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.DraftAssetUpdateRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

	@GetMapping(path = "/{municipalityId}/assets", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<List<Asset>> getAssets(
		@PathVariable String municipalityId,
		@RequestParam String partyId,
		@RequestParam String assetId);

	@GetMapping(path = "/{municipalityId}/asset-drafts", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<List<Asset>> getDraftAssets(
		@PathVariable String municipalityId,
		@RequestParam String partyId,
		@RequestParam String assetId);

	@PostMapping(path = "/{municipalityId}/asset-drafts", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createDraftAsset(
		@PathVariable String municipalityId,
		@RequestParam(required = false) String sourceReference,
		@RequestBody AssetCreateRequest asset);

	@PatchMapping(path = "/{municipalityId}/asset-drafts/{id}", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> updateDraftAsset(
		@PathVariable String municipalityId,
		@PathVariable String id,
		@RequestBody DraftAssetUpdateRequest asset);

	@PostMapping(path = "/{municipalityId}/assets/{assetId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAttachment(
		@PathVariable String municipalityId,
		@PathVariable String assetId,
		@RequestPart("attachment") MultipartFile attachment,
		@RequestPart(name = "category", required = false) String category,
		@RequestPart(name = "description", required = false) String description);

	@DeleteMapping(path = "/{municipalityId}/assets/{id}", produces = ALL_VALUE)
	ResponseEntity<Void> deleteAsset(
		@PathVariable String municipalityId,
		@PathVariable String id);
}
