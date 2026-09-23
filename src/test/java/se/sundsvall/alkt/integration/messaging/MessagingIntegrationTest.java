package se.sundsvall.alkt.integration.messaging;

import generated.se.sundsvall.messaging.SlackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties.Slack;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MessagingIntegrationTest {

	@Mock
	private MessagingClient messagingClientMock;

	private MessagingIntegration messagingIntegration;

	@BeforeEach
	void setup() {
		messagingIntegration = new MessagingIntegration(messagingClientMock, new MessagingProperties(5, 20, new Slack("slack-token", "slack-channel")));
	}

	@Test
	void sendSlackPostsTheMessageToTheConfiguredChannel() {
		messagingIntegration.sendSlack("2281", "Incident");

		verify(messagingClientMock).sendSlack("2281", new SlackRequest().token("slack-token").channel("slack-channel").message("Incident"));
		verifyNoMoreInteractions(messagingClientMock);
	}
}
