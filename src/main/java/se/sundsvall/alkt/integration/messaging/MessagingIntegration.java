package se.sundsvall.alkt.integration.messaging;

import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageBatchResult;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.SlackRequest;
import generated.se.sundsvall.messaging.SnailmailRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties;
import se.sundsvall.dept44.problem.Problem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class MessagingIntegration {

	private final MessagingClient messagingClient;
	private final MessagingProperties properties;

	MessagingIntegration(final MessagingClient messagingClient, final MessagingProperties properties) {
		this.messagingClient = messagingClient;
		this.properties = properties;
	}

	public void sendSlack(final String municipalityId, final String message) {
		messagingClient.sendSlack(municipalityId, new SlackRequest()
			.token(properties.slack().token())
			.channel(properties.slack().channel())
			.message(message));
	}

	public List<Mailbox> getMailboxes(final String municipalityId, final String organizationNumber, final String partyId) {
		return Optional.ofNullable(messagingClient.getMailboxes(municipalityId, organizationNumber, List.of(partyId)).getBody()).orElse(emptyList());
	}

	public MessageBatchResult sendDigitalMail(final String municipalityId, final String organizationNumber, final DigitalMailRequest request) {
		return Optional.ofNullable(messagingClient.sendDigitalMail(municipalityId, organizationNumber, false, request).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "The digital mail came back without a result"));
	}

	/** Each letter gets a batch of its own, which snailmail-sender sends on its schedule. */
	public MessageResult sendSnailMail(final String municipalityId, final SnailmailRequest request) {
		return Optional.ofNullable(messagingClient.sendSnailMail(municipalityId, UUID.randomUUID().toString(), request).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "The snail mail came back without a result"));
	}
}
