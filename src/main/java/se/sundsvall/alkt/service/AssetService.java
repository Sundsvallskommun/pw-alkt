package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.dept44.problem.Problem;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_REJECTION;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPartyId;

@Service
public class AssetService {

	private static final List<String> KNOWN_OUTCOMES = List.of(DECISION_OUTCOME_APPROVAL, DECISION_OUTCOME_REJECTION);

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyAssetsIntegration partyAssetsIntegration;

	AssetService(final SupportManagementIntegration supportManagementIntegration, final PartyAssetsIntegration partyAssetsIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyAssetsIntegration = partyAssetsIntegration;
	}

	public String getDecisionOutcome(final String municipalityId, final String namespace, final String errandId) {
		return supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.map(decision -> toKnownOutcome(decision, errandId))
			.orElse(DECISION_OUTCOME_NONE);
	}

	private static String toKnownOutcome(final Decision decision, final String errandId) {
		return Optional.ofNullable(decision.getOutcome())
			.filter(KNOWN_OUTCOMES::contains)
			.orElseThrow(() -> Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has outcome '%s', expected one of %s"
				.formatted(errandId, decision.getOutcome(), KNOWN_OUTCOMES)));
	}

	public String createAsset(final String municipalityId, final String namespace, final String errandId) {
		final var decision = supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no completed decision to create an asset from".formatted(errandId)));
		if (!DECISION_OUTCOME_APPROVAL.equals(decision.getOutcome())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has outcome '%s', an asset is only created from %s"
				.formatted(errandId, decision.getOutcome(), DECISION_OUTCOME_APPROVAL));
		}
		if (isBlank(decision.getId())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has no id to identify its asset by".formatted(errandId));
		}

		final var partyId = toPartyId(supportManagementIntegration.getErrand(municipalityId, namespace, errandId))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no stakeholder with role '%s'".formatted(errandId, STAKEHOLDER_ROLE_PERMIT_HOLDER)));

		return partyAssetsIntegration.findAssetId(municipalityId, partyId, decision.getId())
			.orElseGet(() -> createAsset(municipalityId, namespace, errandId, decision, partyId));
	}

	private String createAsset(final String municipalityId, final String namespace, final String errandId, final Decision decision, final String partyId) {
		final var attachments = Optional.ofNullable(decision.getAttachments())
			.orElseGet(List::of)
			.stream()
			.map(attachment -> toAssetFile(attachment, supportManagementIntegration.getAttachment(municipalityId, namespace, errandId, attachment.getId())))
			.toList();

		return partyAssetsIntegration.createAsset(municipalityId, namespace, errandId, toAssetCreateRequest(decision, errandId, partyId), attachments);
	}
}
