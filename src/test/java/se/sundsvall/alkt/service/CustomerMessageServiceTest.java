package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Conversation;
import generated.se.sundsvall.supportmanagement.ConversationRequest;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Identifier;
import generated.se.sundsvall.supportmanagement.Message;
import generated.se.sundsvall.supportmanagement.MessageRequest;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.CustomerMessageProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.supportmanagement.model.MessagePage;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.supportmanagement.ConversationType.EXTERNAL;
import static generated.se.sundsvall.supportmanagement.ConversationType.INTERNAL;
import static generated.se.sundsvall.supportmanagement.Identifier.TypeEnum.PARTY_ID;
import static generated.se.sundsvall.supportmanagement.Identifier.TypeEnum.UNKNOWN_DEFAULT_OPEN_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.service.CustomerMessageService.CONVERSATION_TOPIC_CUSTOMER;
import static se.sundsvall.alkt.service.CustomerMessageService.MESSAGE_PAGE_SIZE;

@ExtendWith(MockitoExtension.class)
class CustomerMessageServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";
	private static final String MESSAGE = "processing-started";
	private static final String CONVERSATION_ID = "conversation-id";
	private static final String PARTY = "party-id";
	private static final String CONTENT = "Handläggningen av ditt ärende har påbörjats";

	@Mock
	private SupportManagementIntegration supportManagementIntegrationMock;

	private CustomerMessageService service;

	@BeforeEach
	void setUp() {
		service = new CustomerMessageService(supportManagementIntegrationMock, new CustomerMessageProperties(Map.of(MESSAGE, CONTENT)));
	}

	@Test
	void sendsTheMessageInTheExternalConversationTheCustomerTakesPartIn() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithCustomer());
		when(supportManagementIntegrationMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new Conversation().id("internal-id").type(INTERNAL),
			new Conversation().id("referral-id").type(EXTERNAL).participants(List.of(new Identifier().type(PARTY_ID).value("referral-party-id"))),
			customerConversation()));
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 0, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(List.of(customerMessage()), true));

		assertThat(service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).isTrue();

		verify(supportManagementIntegrationMock).createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, new MessageRequest().content(CONTENT));
		verify(supportManagementIntegrationMock, never()).createConversation(anyString(), anyString(), anyString(), any());
	}

	@Test
	void createsTheExternalConversationWithTheCustomerWhenTheCustomerTakesPartInNone() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithCustomer());
		when(supportManagementIntegrationMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(
			new Conversation().id("internal-id").type(INTERNAL).participants(List.of(new Identifier().type(PARTY_ID).value(PARTY))),
			new Conversation().id("referral-id").type(EXTERNAL).participants(List.of(new Identifier().type(PARTY_ID).value("referral-party-id"))),
			new Conversation().id("no-participants-id").type(EXTERNAL).participants(null)));
		when(supportManagementIntegrationMock.createConversation(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, new ConversationRequest()
			.topic(CONVERSATION_TOPIC_CUSTOMER)
			.type(EXTERNAL)
			.participants(List.of(new Identifier().type(PARTY_ID).value(PARTY))))).thenReturn(CONVERSATION_ID);
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 0, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(null, true));

		assertThat(service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).isTrue();

		verify(supportManagementIntegrationMock).createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, new MessageRequest().content(CONTENT));
	}

	@Test
	void sendsNothingWhenTheProcessHasAlreadySentTheMessage() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithCustomer());
		when(supportManagementIntegrationMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(customerConversation()));
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 0, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(List.of(customerMessage(), processMessage("Another message")), false));
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 1, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(List.of(processMessage(CONTENT)), true));

		assertThat(service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).isFalse();

		verify(supportManagementIntegrationMock, never()).createConversationMessage(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void sendsTheMessageWhenTheSameTextCameFromSomeoneElse() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithCustomer());
		when(supportManagementIntegrationMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(customerConversation()));
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 0, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(List.of(new Message().content(CONTENT), new Message().createdBy(new Identifier().type(PARTY_ID).value(PARTY)).content(CONTENT)), null));

		assertThat(service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).isTrue();

		verify(supportManagementIntegrationMock).createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, new MessageRequest().content(CONTENT));
	}

	/** A paging that never says last must not hold the step, so an empty page ends the search. */
	@Test
	void stopsLookingOnAnEmptyPageThatIsNotTheLast() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(errandWithCustomer());
		when(supportManagementIntegrationMock.getConversations(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(List.of(customerConversation()));
		when(supportManagementIntegrationMock.getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 0, MESSAGE_PAGE_SIZE))
			.thenReturn(new MessagePage(List.of(), false));

		assertThat(service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE)).isTrue();

		verify(supportManagementIntegrationMock, never()).getConversationMessages(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, 1, MESSAGE_PAGE_SIZE);
		verify(supportManagementIntegrationMock).createConversationMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, CONVERSATION_ID, new MessageRequest().content(CONTENT));
	}

	@Test
	void failsWhenTheErrandHasNoCustomer() {
		when(supportManagementIntegrationMock.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(new Errand());

		assertThatThrownBy(() -> service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, MESSAGE))
			.isInstanceOf(Problem.class)
			.hasFieldOrPropertyWithValue("status", NOT_FOUND)
			.hasMessageContaining("Errand 'errand-id' has no stakeholder with role 'PRIMARY'");

		verify(supportManagementIntegrationMock, never()).getConversations(anyString(), anyString(), anyString());
		verify(supportManagementIntegrationMock, never()).createConversation(anyString(), anyString(), anyString(), any());
		verify(supportManagementIntegrationMock, never()).createConversationMessage(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void failsWithoutRetryAndTouchesNoConversationWhenNoTextIsConfigured() {
		assertThatThrownBy(() -> service.sendMessage(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "unknown-message"))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("No text is configured for message 'unknown-message'");

		verifyNoInteractions(supportManagementIntegrationMock);
	}

	private static Errand errandWithCustomer() {
		return new Errand().stakeholders(List.of(new Stakeholder().role(STAKEHOLDER_ROLE_PERMIT_HOLDER).externalId(PARTY)));
	}

	private static Conversation customerConversation() {
		return new Conversation().id(CONVERSATION_ID).type(EXTERNAL).participants(List.of(
			new Identifier().type(UNKNOWN_DEFAULT_OPEN_API).value(PARTY),
			new Identifier().type(PARTY_ID).value(PARTY)));
	}

	private static Message customerMessage() {
		return new Message().createdBy(new Identifier().type(PARTY_ID).value(PARTY)).content("A question");
	}

	private static Message processMessage(final String content) {
		return new Message().createdBy(new Identifier().type(UNKNOWN_DEFAULT_OPEN_API).value("pw-alkt")).content(content);
	}
}
