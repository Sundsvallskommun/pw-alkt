package se.sundsvall.alkt.integration.messaging;

import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageBatchResult;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.SlackRequest;
import generated.se.sundsvall.messaging.SnailmailRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.messaging.configuration.MessagingConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.messaging.url}", configuration = MessagingConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface MessagingClient {

	@PostMapping(path = "/{municipalityId}/slack", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> sendSlack(
		@PathVariable String municipalityId,
		@RequestBody SlackRequest request);

	@PostMapping(path = "/{municipalityId}/{organizationNumber}/mailboxes", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<List<Mailbox>> getMailboxes(
		@PathVariable String municipalityId,
		@PathVariable String organizationNumber,
		@RequestBody List<String> partyIds);

	@PostMapping(path = "/{municipalityId}/{organizationNumber}/digital-mail", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<MessageBatchResult> sendDigitalMail(
		@PathVariable String municipalityId,
		@PathVariable String organizationNumber,
		@RequestParam("async") boolean async,
		@RequestBody DigitalMailRequest request);

	@PostMapping(path = "/{municipalityId}/snail-mail", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<MessageResult> sendSnailMail(
		@PathVariable String municipalityId,
		@RequestParam("batchId") String batchId,
		@RequestBody SnailmailRequest request);

	@PostMapping(path = "/{municipalityId}/snail-mail/batch/{batchId}", produces = ALL_VALUE)
	ResponseEntity<Void> triggerSnailMailBatch(
		@PathVariable String municipalityId,
		@PathVariable String batchId);
}
