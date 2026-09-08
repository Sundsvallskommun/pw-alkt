package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Labels;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.alkt.integration.supportmanagement.configuration.SupportManagementConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static se.sundsvall.alkt.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.support-management.url}", configuration = SupportManagementConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface SupportManagementClient {

	@PostMapping(path = "/{municipalityId}/{namespace}/errands", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@RequestBody Errand errand);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestPart(name = "errandAttachment") MultipartFile file);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/labels", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<Labels> getLabels(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<Errand> getErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PatchMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<Errand> patchErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestHeader("If-Match") String ifMatch,
		@RequestBody Errand errand);

	// TODO: add putDecision for PUT /{municipalityId}/{namespace}/errands/{errandId}/decision once Support Management
	// exposes it (DRAKEN-4744). Needed for a process to write an AUTOMATIC decision back to the errand.

}
