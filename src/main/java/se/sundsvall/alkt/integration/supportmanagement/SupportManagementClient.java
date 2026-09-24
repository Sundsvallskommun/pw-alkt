package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import se.sundsvall.alkt.integration.supportmanagement.configuration.SupportManagementConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.support-management.url}", configuration = SupportManagementConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface SupportManagementClient {

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
		@RequestHeader(value = "X-Trigger-Process", required = false) Boolean triggerProcess,
		@RequestBody Errand errand);

	/** 201 the first time, 200 after that. No X-Trigger-Process, a report is not an errand write. */
	@PutMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/processes/{processInstanceId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<ErrandProcess> reportProcess(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String processInstanceId,
		@RequestBody ErrandProcess report);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/processes", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<ErrandProcesses> getErrandProcesses(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<List<ErrandAttachment>> getAttachments(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	/** The file itself, as bytes. The listing above carries everything about it except its content. */
	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments/{attachmentId}", produces = ALL_VALUE)
	ResponseEntity<byte[]> getAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String attachmentId);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<List<Decision>> getDecisions(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createDecision(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestHeader(value = "X-Trigger-Process", required = false) Boolean triggerProcess,
		@RequestBody Decision decision);

	@PatchMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<Decision> updateDecision(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId,
		@RequestHeader(value = "X-Trigger-Process", required = false) Boolean triggerProcess,
		@RequestBody Decision decision);

	/** Links an attachment already on the errand, nothing is uploaded. */
	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}/attachments/{attachmentId}", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<ErrandAttachment> linkDecisionAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId,
		@PathVariable String attachmentId,
		@RequestHeader(value = "X-Trigger-Process", required = false) Boolean triggerProcess);
}
