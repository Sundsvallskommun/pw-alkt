package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Conversation;
import generated.se.sundsvall.supportmanagement.ConversationRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionOutcome;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcessOverview;
import generated.se.sundsvall.supportmanagement.ErrandProcessReport;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.MessageRequest;
import generated.se.sundsvall.supportmanagement.PageErrand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Statement;
import generated.se.sundsvall.supportmanagement.StatementOutcome;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.model.MessagePage;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.JsonNode;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.util.RemoteAnswers.failWithoutRetryOn;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

@Component
public class SupportManagementIntegration {

	private static final String SERVICE = "Support management";
	// The part name createAttachment declares in SupportManagementClient.
	private static final String ATTACHMENT_PART_NAME = "errandAttachment";

	private final SupportManagementClient supportManagementClient;

	SupportManagementIntegration(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	public Errand getErrand(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getErrand(municipalityId, namespace, errandId).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Errand '%s' came back without content".formatted(errandId)));
	}

	/**
	 * Support Management replaces every parameter of the errand with the ones sent. The errand version turns a change made
	 * since the read into a 412; without one the version is not checked.
	 */
	public void updateErrandParameters(final String municipalityId, final String namespace, final String errandId, final Long errandVersion, final List<Parameter> parameters) {
		final var ifMatch = Optional.ofNullable(errandVersion).map("\"%s\""::formatted).orElse(null);
		supportManagementClient.updateErrandParameters(municipalityId, namespace, errandId, ifMatch, false, parameters);
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

	/**
	 * The value of the JSON parameter of the decision. The case worker writes it, so a decision without it, or with no
	 * value in it, fails without retry.
	 */
	public JsonNode getDecisionJson(final String municipalityId, final String namespace, final String errandId, final String decisionId, final String key) {
		return getJsonValue("decision '%s'".formatted(decisionId), errandId, key,
			() -> supportManagementClient.getDecisionJsonParameter(municipalityId, namespace, errandId, decisionId, key));
	}

	/**
	 * The value of the JSON parameter of the investigation. The case worker writes it, so an investigation without it, or
	 * with no value in it, fails without retry.
	 */
	public JsonNode getInvestigationJson(final String municipalityId, final String namespace, final String errandId, final String investigationId, final String key) {
		return getJsonValue("investigation '%s'".formatted(investigationId), errandId, key,
			() -> supportManagementClient.getInvestigationJsonParameter(municipalityId, namespace, errandId, investigationId, key));
	}

	private static JsonNode getJsonValue(final String owner, final String errandId, final String key, final Supplier<ResponseEntity<JsonParameter>> request) {
		final var response = failWithoutRetryOn(NOT_FOUND, "%s of errand '%s' has no JSON parameter '%s'".formatted(capitalize(owner), errandId, key), request);
		final var parameter = Optional.ofNullable(response.getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "JSON parameter '%s' of %s came back without content".formatted(key, owner)));
		return Optional.ofNullable(parameter.getValue())
			.filter(value -> !value.isNull())
			.orElseThrow(() -> new NonRetryableException("JSON parameter '%s' of %s of errand '%s' has no value".formatted(key, owner, errandId)));
	}

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
			new ByteArrayMultipartFile(ATTACHMENT_PART_NAME, fileName, APPLICATION_PDF_VALUE, content)), SERVICE);
	}

	public List<Investigation> getInvestigations(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getInvestigations(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	public List<Statement> getStatements(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getStatements(municipalityId, namespace, errandId).getBody())
			.orElse(emptyList());
	}

	/** The display name of each decision outcome of the namespace, by its name. */
	public Map<String, String> getDecisionOutcomeNames(final String municipalityId, final String namespace) {
		return Optional.ofNullable(supportManagementClient.getDecisionOutcomes(municipalityId, namespace).getBody()).orElse(emptyList()).stream()
			.filter(outcome -> Objects.nonNull(outcome.getName()) && Objects.nonNull(outcome.getDisplayName()))
			.collect(toMap(DecisionOutcome::getName, DecisionOutcome::getDisplayName, (first, _) -> first));
	}

	/** The display name of each statement outcome of the namespace, by its name. */
	public Map<String, String> getStatementOutcomeNames(final String municipalityId, final String namespace) {
		return Optional.ofNullable(supportManagementClient.getStatementOutcomes(municipalityId, namespace).getBody()).orElse(emptyList()).stream()
			.filter(outcome -> Objects.nonNull(outcome.getName()) && Objects.nonNull(outcome.getDisplayName()))
			.collect(toMap(StatementOutcome::getName, StatementOutcome::getDisplayName, (first, _) -> first));
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
