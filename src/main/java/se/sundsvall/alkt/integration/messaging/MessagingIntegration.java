package se.sundsvall.alkt.integration.messaging;

import generated.se.sundsvall.messaging.SlackRequest;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties;

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
}
