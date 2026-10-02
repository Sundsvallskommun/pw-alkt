package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidUuidConstraintValidator;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_ASSET_ID;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetUpdateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPartyId;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;
import static se.sundsvall.alkt.util.FailureDescription.describe;

@Service
public class AssetService {

	private static final ValidUuidConstraintValidator UUID_VALIDATOR = new ValidUuidConstraintValidator();

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyAssetsIntegration partyAssetsIntegration;
	private final TemplatingIntegration templatingIntegration;

	AssetService(final SupportManagementIntegration supportManagementIntegration, final PartyAssetsIntegration partyAssetsIntegration, final TemplatingIntegration templatingIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyAssetsIntegration = partyAssetsIntegration;
		this.templatingIntegration = templatingIntegration;
	}

	public String getDecisionOutcome(final String municipalityId, final String namespace, final String errandId) {
		return supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.map(decision -> toKnownOutcome(decision, errandId))
			.orElse(DECISION_OUTCOME_NONE);
	}

	private static String toKnownOutcome(final Decision decision, final String errandId) {
		return Optional.ofNullable(decision.getOutcome())
			.filter(DECISION_OUTCOMES::contains)
			.orElseThrow(() -> Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has outcome '%s', expected one of %s"
				.formatted(errandId, decision.getOutcome(), new TreeSet<>(DECISION_OUTCOMES))));
	}

	/** Without a certificate template the asset gets no certificate. */
	public String findOrCreateAsset(final String municipalityId, final String namespace, final String errandId, final String certificateTemplate) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		if (isBlank(decision.getId())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has no id to identify its asset by".formatted(errandId));
		}

		final var partyId = toPartyId(supportManagementIntegration.getErrand(municipalityId, namespace, errandId))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no stakeholder with role '%s'".formatted(errandId, STAKEHOLDER_ROLE_PERMIT_HOLDER)));

		return partyAssetsIntegration.findAssetId(municipalityId, partyId, decision.getId())
			.orElseGet(() -> createAsset(municipalityId, namespace, errandId, decision, partyId, certificateTemplate));
	}

	/**
	 * Changes the permit the errand names. party-assets keeps the earlier content as a revision. Without a certificate
	 * template the certificate is left as it is.
	 */
	public String updateAsset(final String municipalityId, final String namespace, final String errandId, final String certificateTemplate) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);

		final var assetId = toAssetId(errand)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' names no asset to change".formatted(errandId)));
		// Why: party-assets answers an id that is not a UUID with 400, which reaches us as 502 and would be retried in vain.
		if (!UUID_VALIDATOR.isValid(assetId)) {
			throw new NonRetryableException("Errand '%s' names asset '%s', which is not an asset id".formatted(errandId, assetId));
		}
		final var partyId = toPartyId(errand)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no stakeholder with role '%s'".formatted(errandId, STAKEHOLDER_ROLE_PERMIT_HOLDER)));
		final var versioned = partyAssetsIntegration.getAsset(municipalityId, assetId);
		final var asset = versioned.asset();
		if (asset.getStatus() != ACTIVE) {
			throw new NonRetryableException("Asset '%s' has status %s, only an active permit can be changed".formatted(assetId, asset.getStatus()));
		}
		if (!partyId.equals(asset.getPartyId())) {
			throw new NonRetryableException("Asset '%s' does not belong to the permit holder of errand '%s'".formatted(assetId, errandId));
		}

		// Why: rendered before the asset changes, so a decision that does not fill the template leaves the asset as it was.
		final var update = toAssetUpdateRequest(asset, decision, errandId);
		final var certificate = isNotBlank(certificateTemplate)
			? toCertificateFile(templatingIntegration.renderPdf(municipalityId, certificateTemplate, toCertificateParameters(update.getAdditionalParameters(), decision)))
			: null;

		partyAssetsIntegration.updateAsset(municipalityId, assetId, versioned.version(), update);
		if (certificate != null) {
			partyAssetsIntegration.replaceCertificate(municipalityId, assetId, certificate);
		}
		return assetId;
	}

	private Decision getApprovingDecision(final String municipalityId, final String namespace, final String errandId) {
		final var decision = supportManagementIntegration.getCompletedDecision(municipalityId, namespace, errandId)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Errand '%s' has no completed decision to base an asset on".formatted(errandId)));
		if (decision.getOutcome() == null || !DECISION_OUTCOMES_CREATING_ASSET.contains(decision.getOutcome())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has outcome '%s', an asset is only created or changed from one of %s"
				.formatted(errandId, decision.getOutcome(), new TreeSet<>(DECISION_OUTCOMES_CREATING_ASSET)));
		}
		return decision;
	}

	private static Optional<String> toAssetId(final Errand errand) {
		return Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_ASSET_ID.equals(parameter.getKey()))
			.flatMap(parameter -> Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream())
			.filter(StringUtils::isNotBlank)
			.findFirst();
	}

	// Why: the certificate shows the whole permit, so it is rendered from the parameters the asset gets, conditions
	// included. Those of the decision only fill in a placeholder the asset has no value for.
	private static Map<String, Object> toCertificateParameters(final Map<String, String> assetParameters, final Decision decision) {
		final var parameters = new LinkedHashMap<>(toTemplateParameters(decision));
		parameters.putAll(assetParameters);
		return parameters;
	}

	// Why: each file is fetched just before its upload, so only one of them is held in memory at a time.
	private String createAsset(final String municipalityId, final String namespace, final String errandId, final Decision decision, final String partyId,
		final String certificateTemplate) {
		final var assetId = partyAssetsIntegration.createDraftAsset(municipalityId, namespace, errandId, toAssetCreateRequest(decision, errandId, partyId));

		try {
			Optional.ofNullable(decision.getAttachments()).orElse(emptyList())
				.forEach(attachment -> partyAssetsIntegration.addAttachmentToDraft(municipalityId, assetId,
					toAssetFile(attachment, supportManagementIntegration.getAttachment(municipalityId, namespace, errandId, attachment.getId()))));
			if (isNotBlank(certificateTemplate)) {
				partyAssetsIntegration.addAttachmentToDraft(municipalityId, assetId,
					toCertificateFile(templatingIntegration.renderPdf(municipalityId, certificateTemplate, toTemplateParameters(decision))));
			}
			partyAssetsIntegration.activateAsset(municipalityId, assetId);
		} catch (final NonRetryableException e) {
			throw new NonRetryableException(removeDraftAsset(municipalityId, assetId, e), e);
		} catch (final RuntimeException e) {
			throw Problem.valueOf(BAD_GATEWAY, removeDraftAsset(municipalityId, assetId, e));
		}

		return assetId;
	}

	private String removeDraftAsset(final String municipalityId, final String assetId, final RuntimeException cause) {
		try {
			partyAssetsIntegration.removeDraftAsset(municipalityId, assetId);
			return "The draft asset could not be completed and was removed again: %s".formatted(describe(cause));
		} catch (final RuntimeException e) {
			// The id is the only way to find the asset that is left behind, so it travels with both failures.
			return "Draft asset '%s' could not be completed (%s) and could not be removed again: %s".formatted(assetId, describe(cause), describe(e));
		}
	}
}
