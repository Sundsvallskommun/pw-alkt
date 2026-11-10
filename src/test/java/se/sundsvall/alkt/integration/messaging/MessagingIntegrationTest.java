package se.sundsvall.alkt.integration.messaging;

import generated.se.sundsvall.messaging.DigitalMailRequest;
import generated.se.sundsvall.messaging.Mailbox;
import generated.se.sundsvall.messaging.MessageBatchResult;
import generated.se.sundsvall.messaging.MessageResult;
import generated.se.sundsvall.messaging.SlackRequest;
import generated.se.sundsvall.messaging.SnailmailRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties;
import se.sundsvall.alkt.integration.messaging.configuration.MessagingProperties.Slack;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class MessagingIntegrationTest {

	private static final String PARTY_ID = "f427952b-247c-4d3b-b081-675a467b3619";

	@Mock
	private MessagingClient messagingClientMock;

	@Captor
	private ArgumentCaptor<String> batchIdCaptor;

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

	@Test
	void getMailboxesAsksForTheMailboxOfTheParty() {
		final var mailbox = new Mailbox().partyId(PARTY_ID).supplier("Kivra").reachable(true);
		when(messagingClientMock.getMailboxes("2281", "2120002411", List.of(PARTY_ID))).thenReturn(ResponseEntity.ok(List.of(mailbox)));

		assertThat(messagingIntegration.getMailboxes("2281", "2120002411", PARTY_ID)).containsExactly(mailbox);
	}

	@Test
	void getMailboxesWithoutBodyGivesNoMailbox() {
		when(messagingClientMock.getMailboxes("2281", "2120002411", List.of(PARTY_ID))).thenReturn(ResponseEntity.ok().build());

		assertThat(messagingIntegration.getMailboxes("2281", "2120002411", PARTY_ID)).isEmpty();
	}

	@Test
	void sendDigitalMailSendsSynchronously() {
		final var request = new DigitalMailRequest().subject("Ditt serveringstillstånd");
		final var result = new MessageBatchResult().batchId(UUID.fromString("9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b"));
		when(messagingClientMock.sendDigitalMail("2281", "2120002411", false, request)).thenReturn(ResponseEntity.status(201).body(result));

		assertThat(messagingIntegration.sendDigitalMail("2281", "2120002411", request)).isSameAs(result);
	}

	@Test
	void sendDigitalMailWithoutResultFailsWithRetry() {
		final var request = new DigitalMailRequest().subject("Ditt serveringstillstånd");
		when(messagingClientMock.sendDigitalMail("2281", "2120002411", false, request)).thenReturn(ResponseEntity.status(201).build());

		assertThatThrownBy(() -> messagingIntegration.sendDigitalMail("2281", "2120002411", request))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	@Test
	void sendSnailMailWithoutResultFailsWithRetry() {
		final var request = new SnailmailRequest().department("SBK(Alkohol och tobak)");
		when(messagingClientMock.sendSnailMail(eq("2281"), anyString(), same(request))).thenReturn(ResponseEntity.status(201).build());

		assertThatThrownBy(() -> messagingIntegration.sendSnailMail("2281", request))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	@Test
	void sendSnailMailPutsTheLetterInABatchOfItsOwn() {
		final var request = new SnailmailRequest().department("SBK(Alkohol och tobak)");
		final var result = new MessageResult().messageId(UUID.fromString("0b6c1d2e-8f3a-4b5c-9d7e-1a2b3c4d5e6f"));
		when(messagingClientMock.sendSnailMail(eq("2281"), batchIdCaptor.capture(), same(request))).thenReturn(ResponseEntity.status(201).body(result));

		assertThat(messagingIntegration.sendSnailMail("2281", request)).isSameAs(result);
		messagingIntegration.sendSnailMail("2281", request);

		assertThat(batchIdCaptor.getAllValues()).hasSize(2).doesNotHaveDuplicates()
			.allSatisfy(batchId -> assertThat(UUID.fromString(batchId)).hasToString(batchId));
	}
}
