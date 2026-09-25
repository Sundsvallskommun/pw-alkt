package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
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

	public void reportProcess(final String municipalityId, final String namespace, final String errandId, final String processInstanceId, final ErrandProcess report) {
		supportManagementClient.reportProcess(municipalityId, namespace, errandId, processInstanceId, report);
	}

	public List<ErrandProcess> getErrandProcesses(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getErrandProcesses(municipalityId, namespace, errandId).getBody())
			.map(ErrandProcesses::getProcesses)
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
}
