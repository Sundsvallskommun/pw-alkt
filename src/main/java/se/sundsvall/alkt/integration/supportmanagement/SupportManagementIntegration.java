package se.sundsvall.alkt.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ErrandProcesses;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class SupportManagementIntegration {

	private final SupportManagementClient supportManagementClient;

	SupportManagementIntegration(final SupportManagementClient supportManagementClient) {
		this.supportManagementClient = supportManagementClient;
	}

	public void reportProcess(final String municipalityId, final String namespace, final String errandId, final String processInstanceId, final ErrandProcess report) {
		supportManagementClient.reportProcess(municipalityId, namespace, errandId, processInstanceId, report);
	}

	public List<ErrandProcess> getErrandProcesses(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getErrandProcesses(municipalityId, namespace, errandId).getBody())
			.map(ErrandProcesses::getProcesses)
			.orElseGet(List::of);
	}

	public List<ErrandAttachment> getAttachments(final String municipalityId, final String namespace, final String errandId) {
		return Optional.ofNullable(supportManagementClient.getAttachments(municipalityId, namespace, errandId).getBody())
			.orElseGet(List::of);
	}

	/** An empty file decodes to an empty array, so this only fires when the answer carries no body at all. */
	public byte[] getAttachment(final String municipalityId, final String namespace, final String errandId, final String attachmentId) {
		return Optional.ofNullable(supportManagementClient.getAttachment(municipalityId, namespace, errandId, attachmentId).getBody())
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Attachment '%s' of errand '%s' came back without content".formatted(attachmentId, errandId)));
	}

	// X-Trigger-Process is false because a write from the process that owns the errand must not wake that same process.
	// A 409 is passed on as it comes: Support Management uses it both for an errand that already has a decision and for
	// one whose process is over, and the two are not distinguishable, so neither can be treated as already done here.
	public void createDecision(final String municipalityId, final String namespace, final String errandId, final Decision decision) {
		supportManagementClient.createDecision(municipalityId, namespace, errandId, false, decision);
	}
}
