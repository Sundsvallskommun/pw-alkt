package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toAutomaticDecision;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecisionCompletion;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecisionTitle;

@Service
public class DecisionService {

	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");

	private final SupportManagementIntegration supportManagementIntegration;

	DecisionService(final SupportManagementIntegration supportManagementIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
	}

	/**
	 * Support Management locks a completed decision, attachments included, so the decision is written as a draft, given
	 * every attachment of the errand and completed last. A rerun picks up the draft an earlier attempt left behind.
	 */
	public String createDecision(final String municipalityId, final String namespace, final String errandId, final String processKey) {
		final var title = toDecisionTitle(processKey);

		final var decisions = supportManagementIntegration.getDecisions(municipalityId, namespace, errandId);
		final var completed = decisions.stream().filter(decision -> DECISION_STATUS_COMPLETED.equals(decision.getStatus())).findFirst();
		if (completed.isPresent()) {
			return completed.get().getId();
		}

		final var decidedAt = OffsetDateTime.now(SWEDISH_TIME);
		final var draft = decisions.stream().findFirst();
		final var decisionId = draft.map(Decision::getId)
			.orElseGet(() -> supportManagementIntegration.createDecision(municipalityId, namespace, errandId,
				toAutomaticDecision(title, supportManagementIntegration.getErrand(municipalityId, namespace, errandId), LocalDate.now(SWEDISH_TIME), decidedAt)));

		final var linked = draft.map(Decision::getAttachments).orElse(emptyList()).stream().map(ErrandAttachment::getId).toList();
		supportManagementIntegration.getAttachments(municipalityId, namespace, errandId).stream()
			.map(ErrandAttachment::getId)
			.filter(attachmentId -> !linked.contains(attachmentId))
			.forEach(attachmentId -> supportManagementIntegration.linkDecisionAttachment(municipalityId, namespace, errandId, decisionId, attachmentId));

		supportManagementIntegration.updateDecision(municipalityId, namespace, errandId, decisionId, toDecisionCompletion(decidedAt));
		return decisionId;
	}
}
