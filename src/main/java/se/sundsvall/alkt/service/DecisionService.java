package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.DECISION_METHOD_AUTOMATIC;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_DRAFT;
import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toAutomaticDecision;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toChangeDraft;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toChangeDraftTitle;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecisionCompletion;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecisionTitle;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toNoPermitHolderMessage;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPartyId;

@Service
public class DecisionService {

	private final SupportManagementIntegration supportManagementIntegration;
	private final AssetService assetService;
	private final Clock clock;

	DecisionService(final SupportManagementIntegration supportManagementIntegration, final AssetService assetService, final Clock clock) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.assetService = assetService;
		this.clock = clock;
	}

	/**
	 * Support Management locks a completed decision, attachments included, so the decision is written as a draft, given
	 * every attachment of the errand and completed last. A rerun picks up the draft an earlier attempt left behind. Any
	 * other decision on the errand is the case worker's, so it is left alone and the step goes to an incident.
	 */
	public String approveAutomatically(final String municipalityId, final String namespace, final String errandId, final String processKey) {
		final var title = toDecisionTitle(processKey);

		final var decisions = supportManagementIntegration.getDecisions(municipalityId, namespace, errandId);
		final var completed = decisions.stream().filter(decision -> DECISION_STATUS_COMPLETED.equals(decision.getStatus())).findFirst();
		if (completed.isPresent()) {
			return completed.get().getId();
		}

		final var decidedAt = OffsetDateTime.now(clock);
		final var draft = decisions.stream().filter(DecisionService::isOwnDraft).findFirst();
		if (draft.isEmpty() && !decisions.isEmpty()) {
			throw new NonRetryableException("Errand %s already has a decision pw-alkt did not make, so it is left to the case worker".formatted(errandId));
		}

		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		// Why: the next step creates the permit for this holder. Without one the approval would be locked with no permit behind
		// it.
		if (toPartyId(errand).isEmpty()) {
			throw new NonRetryableException(toNoPermitHolderMessage(errandId));
		}

		final var decisionId = draft.map(Decision::getId)
			.orElseGet(() -> supportManagementIntegration.createDecision(municipalityId, namespace, errandId,
				toAutomaticDecision(title, errand, decidedAt.toLocalDate(), decidedAt)));

		final var linked = draft.map(Decision::getAttachments).orElse(emptyList()).stream()
			.map(ErrandAttachment::getId)
			.toList();
		supportManagementIntegration.getAttachments(municipalityId, namespace, errandId).stream()
			.map(ErrandAttachment::getId)
			.filter(attachmentId -> !linked.contains(attachmentId))
			.forEach(attachmentId -> supportManagementIntegration.linkDecisionAttachment(municipalityId, namespace, errandId, decisionId, attachmentId));

		supportManagementIntegration.updateDecision(municipalityId, namespace, errandId, decisionId, toDecisionCompletion(decidedAt));
		return decisionId;
	}

	/**
	 * Drafts the decision of a change errand from the change the customer asks for. An errand has one decision at most, so
	 * one already there is the draft of an earlier attempt.
	 */
	public String createChangeDraft(final String municipalityId, final String namespace, final String errandId, final String permitType, final String processKey) {
		final var title = toChangeDraftTitle(processKey);

		return supportManagementIntegration.getDecisions(municipalityId, namespace, errandId).stream()
			.findFirst()
			.map(Decision::getId)
			.orElseGet(() -> {
				final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
				// Why: a fault in the permit the customer chose shows now, not once the decision is locked.
				assetService.getPermitToChange(municipalityId, errand, errandId, permitType);
				return supportManagementIntegration.createDecision(municipalityId, namespace, errandId, toChangeDraft(errand, title, OffsetDateTime.now(clock)));
			});
	}

	private static boolean isOwnDraft(final Decision decision) {
		return DECISION_STATUS_DRAFT.equals(decision.getStatus())
			&& DECISION_METHOD_AUTOMATIC.equals(decision.getMethod())
			&& PROCESS_SERVICE.equals(decision.getDecidedBy());
	}
}
