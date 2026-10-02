package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Conversation;
import generated.se.sundsvall.supportmanagement.ConversationRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcessOverview;
import generated.se.sundsvall.supportmanagement.ErrandProcessReport;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.MessageRequest;
import generated.se.sundsvall.supportmanagement.PageErrand;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.supportmanagement.model.MessagePage;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;
import se.sundsvall.dept44.problem.Problem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class SupportManagementIntegration {

	private static final String SERVICE = "Support management";

	private final SupportManagementClient supportManagementClient;

	SupportManagementIntegration(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	public Errand getErrand(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getErrand(municipalityId, namespace, errandId).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Errand '%s' came back without content".formatted(errandId)));
	}

	public void reportProcess(final String municipalityId, final String namespace, final String errandId, final String processInstanceId, final ErrandProcessReport report) {
		supportManagementClient.reportProcess(municipalityId, namespace, errandId, processInstanceId, report);
	}

	public List<ErrandProcess> getErrandProcesses(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getErrandProcesses(municipalityId, namespace, errandId).getBody())
			.map(ErrandProcessOverview::getProcesses)
			.orElse(emptyList());
	}

	public List<ErrandAttachment> getAttachments(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getAttachments(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	/**
	 * Feign answers with a null body both when the file is zero bytes and when the answer carries no content at all. The
	 * two cannot be told apart here, so both are treated as a fault.
	 */
	public byte[] getAttachment(final String municipalityId, final String namespace, final String errandId, final String attachmentId) {
		return Optional.ofNullable(supportManagementClient.getAttachment(municipalityId, namespace, errandId, attachmentId).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Attachment '%s' of errand '%s' came back without content".formatted(attachmentId, errandId)));
	}

	public Optional<Decision> getCompletedDecision(final String municipalityId, final String namespace, final String errandId) {
		return getDecisions(municipalityId, namespace, errandId).stream()
			.filter(decision -> DECISION_STATUS_COMPLETED.equals(decision.getStatus()))
			.findFirst();
	}

	public List<Decision> getDecisions(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getDecisions(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	// Why: the decision is written by the process of the errand, and must not wake that process again.
	public String createDecision(final String municipalityId, final String namespace, final String errandId, final Decision decision) {
		return getIdOfCreatedResource(supportManagementClient.createDecision(municipalityId, namespace, errandId, false, decision), SERVICE);
	}

	public void updateDecision(final String municipalityId, final String namespace, final String errandId, final String decisionId, final Decision decision) {
		supportManagementClient.updateDecision(municipalityId, namespace, errandId, decisionId, false, decision);
	}

	public void linkDecisionAttachment(final String municipalityId, final String namespace, final String errandId, final String decisionId, final String attachmentId) {
		supportManagementClient.linkDecisionAttachment(municipalityId, namespace, errandId, decisionId, attachmentId, false);
	}

	public List<Conversation> getConversations(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getConversations(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	public String createConversation(final String municipalityId, final String namespace, final String errandId, final ConversationRequest conversation) {
		return getIdOfCreatedResource(supportManagementClient.createConversation(municipalityId, namespace, errandId, false, conversation), SERVICE);
	}

	public MessagePage getConversationMessages(final String municipalityId, final String namespace, final String errandId, final String conversationId, final int page, final int size) {
		return Optional.ofNullable(supportManagementClient.getConversationMessages(municipalityId, namespace, errandId, conversationId, page, size).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Messages of conversation '%s' of errand '%s' came back without content".formatted(conversationId, errandId)));
	}

	public void createConversationMessage(final String municipalityId, final String namespace, final String errandId, final String conversationId, final MessageRequest message) {
		supportManagementClient.createConversationMessage(municipalityId, namespace, errandId, conversationId, false, message);
	}

	public String createPdfAttachment(final String municipalityId, final String namespace, final String errandId, final String fileName, final byte[] content) {
		return getIdOfCreatedResource(supportManagementClient.createAttachment(municipalityId, namespace, errandId, false,
			new ByteArrayMultipartFile("errandAttachment", fileName, APPLICATION_PDF_VALUE, content)), SERVICE);
	}

	public List<Investigation> getInvestigations(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getInvestigations(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	public Optional<String> findErrandIdByExternalTag(final String municipalityId, final String namespace, final String key, final String value) {
		return Optional.ofNullable(supportManagementClient.findErrands(municipalityId, namespace, "externalTags.key:'%s' and externalTags.value:'%s'".formatted(key, value)).getBody())
			.map(PageErrand::getContent)
			.orElse(emptyList())
			.stream()
			.map(Errand::getId)
			.findFirst();
	}

	public String createErrand(final String municipalityId, final String namespace, final String referredFrom, final Errand errand) {
		return getIdOfCreatedResource(supportManagementClient.createErrand(municipalityId, namespace, referredFrom, errand), SERVICE);
	}
}
