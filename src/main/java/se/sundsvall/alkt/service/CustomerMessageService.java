package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Conversation;
import generated.se.sundsvall.supportmanagement.ConversationRequest;
import generated.se.sundsvall.supportmanagement.Identifier;
import generated.se.sundsvall.supportmanagement.Message;
import generated.se.sundsvall.supportmanagement.MessageRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.CustomerMessageProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.supportmanagement.ConversationType.EXTERNAL;
import static generated.se.sundsvall.supportmanagement.Identifier.TypeEnum.PARTY_ID;
import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.CONVERSATION_TOPIC_CUSTOMER;
import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPartyId;

@Service
public class CustomerMessageService {

	static final int MESSAGE_PAGE_SIZE = 100;

	private final SupportManagementIntegration supportManagementIntegration;
	private final CustomerMessageProperties properties;

	CustomerMessageService(final SupportManagementIntegration supportManagementIntegration, final CustomerMessageProperties properties) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.properties = properties;
	}

	/**
	 * Returns false when the same message is already in the conversation: a step that is run again must not send it twice.
	 */
	public boolean sendMessage(final String municipalityId, final String namespace, final String errandId, final String message) {
		final var content = Optional.ofNullable(properties.texts().get(message))
			.orElseThrow(() -> new NonRetryableException("No text is configured for message '%s'".formatted(message)));
		final var conversationId = findOrCreateConversation(municipalityId, namespace, errandId);

		if (isAlreadySent(municipalityId, namespace, errandId, conversationId, content)) {
			return false;
		}

		supportManagementIntegration.createConversationMessage(municipalityId, namespace, errandId, conversationId, new MessageRequest().content(content));
		return true;
	}

	// Why: Mina sidor shows the first external conversation of the errand, so one it created itself is used rather than a
	// second one.
	private String findOrCreateConversation(final String municipalityId, final String namespace, final String errandId) {
		return supportManagementIntegration.getConversations(municipalityId, namespace, errandId).stream()
			.filter(conversation -> EXTERNAL == conversation.getType())
			.map(Conversation::getId)
			.findFirst()
			.orElseGet(() -> createConversation(municipalityId, namespace, errandId));
	}

	private String createConversation(final String municipalityId, final String namespace, final String errandId) {
		final var partyId = toPartyId(supportManagementIntegration.getErrand(municipalityId, namespace, errandId))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no stakeholder with role '%s'".formatted(errandId, STAKEHOLDER_ROLE_PERMIT_HOLDER)));

		return supportManagementIntegration.createConversation(municipalityId, namespace, errandId, new ConversationRequest()
			.topic(CONVERSATION_TOPIC_CUSTOMER)
			.type(EXTERNAL)
			.participants(List.of(new Identifier().type(PARTY_ID).value(partyId))));
	}

	private boolean isAlreadySent(final String municipalityId, final String namespace, final String errandId, final String conversationId, final String content) {
		for (var page = 0;; page++) {
			final var messages = supportManagementIntegration.getConversationMessages(municipalityId, namespace, errandId, conversationId, page, MESSAGE_PAGE_SIZE);
			final var onPage = Optional.ofNullable(messages.getContent()).orElse(emptyList());
			if (onPage.stream().anyMatch(message -> isSentByProcess(message, content))) {
				return true;
			}
			// An empty page ends the search as well, so a paging that never says last cannot hold the step.
			if (onPage.isEmpty() || !FALSE.equals(messages.getLast())) {
				return false;
			}
		}
	}

	private static boolean isSentByProcess(final Message message, final String content) {
		return Optional.ofNullable(message.getCreatedBy())
			.map(Identifier::getValue)
			.filter(PROCESS_SERVICE::equals)
			.isPresent() && content.equals(message.getContent());
	}
}
