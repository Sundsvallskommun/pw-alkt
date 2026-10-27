package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.alkt.service.model.AssetType;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidUuidConstraintValidator;
import se.sundsvall.dept44.problem.Problem;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_ASSET_ID;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetClosureRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetUpdateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPartyId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.templating.mapper.TemplatingMapper.toTemplateParameters;
import static se.sundsvall.alkt.util.FailureDescription.describe;

@Service
public class AssetService {

	private static final ValidUuidConstraintValidator UUID_VALIDATOR = new ValidUuidConstraintValidator();

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyAssetsIntegration partyAssetsIntegration;
	private final TemplatingIntegration templatingIntegration;
	private final Clock clock;

	AssetService(final SupportManagementIntegration supportManagementIntegration, final PartyAssetsIntegration partyAssetsIntegration, final TemplatingIntegration templatingIntegration,
		final Clock clock) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyAssetsIntegration = partyAssetsIntegration;
		this.templatingIntegration = templatingIntegration;
		this.clock = clock;
	}

	/**
	 * An asset type without a certificate template gets no certificate, and without a restaurant number the asset carries
	 * none. A decision with a validTo must end after today, as party-assets refuses to activate the asset otherwise.
	 */
	public String findOrCreateAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType, final String restaurantNumber) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		if (isBlank(decision.getId())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has no id to identify its asset by".formatted(errandId));
		}

		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var partyId = toPartyId(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
		final var assetMunicipalityId = toPremisesMunicipalityId(errand);

		return partyAssetsIntegration.findAsset(assetMunicipalityId, partyId, decision.getId())
			.map(asset -> {
				requireAssetType(asset, assetType);
				return asset.getId();
			})
			.orElseGet(() -> {
				requireNotEnded(decision, errandId);
				return createAsset(municipalityId, assetMunicipalityId, namespace, errandId, decision, toAssetCreateRequest(decision, errandId, partyId, assetType, restaurantNumber), assetType);
			});
	}

	/**
	 * Changes the permit the errand names. party-assets keeps the earlier content as a revision. An asset type without a
	 * certificate template leaves the certificate as it is.
	 */
	public String updateAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		requireNotPassed(decision, errandId);
		// Why: checked again at the decision, as the permit may have been deactivated while the errand was handled.
		final var versioned = getPermitToChange(supportManagementIntegration.getErrand(municipalityId, namespace, errandId), errandId, assetType);
		final var asset = versioned.asset();
		final var assetId = asset.getId();

		// Why: rendered before the asset changes, so a decision that does not fill the template leaves the asset as it was.
		// Rendered from the parameters the asset gets, since the certificate shows the whole permit.
		final var update = toAssetUpdateRequest(asset, decision);
		final var certificate = assetType.getCertificateTemplate()
			.map(template -> toCertificateFile(templatingIntegration.renderPdf(versioned.municipalityId(), template, toTemplateParameters(decision, update.getAdditionalParameters()))));

		// Why: a rerun after the certificate failed finds the change already made, and every PATCH adds a revision.
		if (changes(update, asset)) {
			partyAssetsIntegration.updateAsset(versioned.municipalityId(), assetId, versioned.version(), update);
		}
		certificate.ifPresent(file -> partyAssetsIntegration.replaceCertificate(versioned.municipalityId(), assetId, file));
		return assetId;
	}

	/**
	 * Checks the permit the errand names before the case worker handles the errand, and answers with its id. A permit that
	 * has already ended passes, as the closure then has nothing to do.
	 */
	public String checkPermit(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var asset = getPermitOfTheErrand(supportManagementIntegration.getErrand(municipalityId, namespace, errandId), errandId, assetType).asset();
		if (asset.getStatus() != EXPIRED) {
			requireActive(asset);
		}
		return asset.getId();
	}

	/** Ends the permit the errand names. The permit certificate stays as it is. */
	public String closeAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		final var versioned = getPermitOfTheErrand(supportManagementIntegration.getErrand(municipalityId, namespace, errandId), errandId, assetType);
		final var asset = versioned.asset();
		final var closure = toAssetClosureRequest(asset, decision, LocalDate.now(clock));

		// Why: a rerun after the PATCH went through finds the permit already ended, or already set to end.
		if (!isClosed(asset, closure)) {
			requireActive(asset);
			requireNotBeforeIssued(asset, closure, errandId);
			partyAssetsIntegration.updateAsset(versioned.municipalityId(), asset.getId(), versioned.version(), closure);
		}
		return asset.getId();
	}

	private static boolean isClosed(final Asset asset, final AssetUpdateRequest closure) {
		return asset.getStatus() == EXPIRED
			|| (asset.getStatus() == ACTIVE && closure.getStatus() == null && closure.getValidTo().equals(asset.getValidTo()));
	}

	private static void requireNotBeforeIssued(final Asset asset, final AssetUpdateRequest closure, final String errandId) {
		Optional.ofNullable(asset.getIssued())
			.filter(issued -> closure.getValidTo().isBefore(issued))
			.ifPresent(issued -> {
				throw new NonRetryableException("Decision of errand '%s' ends asset '%s' on %s, before it was issued on %s".formatted(errandId, asset.getId(), closure.getValidTo(), issued));
			});
	}

	/** The permit the errand names, which the customer chose, so a fault in it is not something a retry fixes. */
	public VersionedAsset getPermitToChange(final Errand errand, final String errandId, final AssetType assetType) {
		final var versioned = getPermitOfTheErrand(errand, errandId, assetType);
		requireActive(versioned.asset());
		return versioned;
	}

	private VersionedAsset getPermitOfTheErrand(final Errand errand, final String errandId, final AssetType assetType) {
		final var assetId = toAssetId(errand)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' names no asset to change".formatted(errandId)));
		// Why: party-assets answers an id that is not a UUID with 400, which would be retried in vain.
		if (!UUID_VALIDATOR.isValid(assetId)) {
			throw new NonRetryableException("Errand '%s' names asset '%s', which is not an asset id".formatted(errandId, assetId));
		}
		final var partyId = toPartyId(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
		final var versioned = partyAssetsIntegration.getAsset(assetId);
		final var asset = versioned.asset();
		if (!partyId.equals(asset.getPartyId())) {
			throw new NonRetryableException("Asset '%s' does not belong to the permit holder of errand '%s'".formatted(assetId, errandId));
		}
		requireAssetType(asset, assetType);
		return versioned;
	}

	private static void requireAssetType(final Asset asset, final AssetType assetType) {
		if (!assetType.getValue().equals(asset.getType())) {
			throw new NonRetryableException("Asset '%s' is of type '%s', the process handles only type '%s'".formatted(asset.getId(), asset.getType(), assetType.getValue()));
		}
	}

	private static void requireActive(final Asset asset) {
		if (asset.getStatus() != ACTIVE) {
			throw new NonRetryableException("Asset '%s' has status %s, only an active permit can be handled".formatted(asset.getId(), asset.getStatus()));
		}
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

	private static boolean changes(final AssetUpdateRequest update, final Asset asset) {
		return !update.getAdditionalParameters().equals(Optional.ofNullable(asset.getAdditionalParameters()).orElse(emptyMap()))
			|| (update.getValidTo() != null && !update.getValidTo().equals(asset.getValidTo()));
	}

	// Why: a change may end the permit today at the earliest, never backdate its end.
	private void requireNotPassed(final Decision decision, final String errandId) {
		Optional.ofNullable(decision.getValidTo())
			.filter(validTo -> validTo.isBefore(LocalDate.now(clock)))
			.ifPresent(validTo -> {
				throw new NonRetryableException("Decision of errand '%s' is valid to %s, which has passed, so the permit is not changed".formatted(errandId, validTo));
			});
	}

	private void requireNotEnded(final Decision decision, final String errandId) {
		Optional.ofNullable(decision.getValidTo())
			.filter(validTo -> !validTo.isAfter(LocalDate.now(clock)))
			.ifPresent(validTo -> {
				throw new NonRetryableException("Decision of errand '%s' is valid to %s, which is not after today, so the permit cannot be activated".formatted(errandId, validTo));
			});
	}

	// Why: each file is fetched just before its upload, so only one of them is held in memory at a time.
	private String createAsset(final String municipalityId, final String assetMunicipalityId, final String namespace, final String errandId, final Decision decision, final AssetCreateRequest request,
		final AssetType assetType) {
		final var assetId = partyAssetsIntegration.createDraftAsset(assetMunicipalityId, namespace, errandId, request);

		try {
			Optional.ofNullable(decision.getAttachments()).orElse(emptyList())
				.forEach(attachment -> partyAssetsIntegration.addAttachmentToDraft(assetMunicipalityId, assetId,
					toAssetFile(attachment, supportManagementIntegration.getAttachment(municipalityId, namespace, errandId, attachment.getId()))));
			// Why: rendered from the parameters the asset gets, as on a change, so the certificate shows the restaurant number.
			assetType.getCertificateTemplate().ifPresent(template -> partyAssetsIntegration.addAttachmentToDraft(assetMunicipalityId, assetId,
				toCertificateFile(templatingIntegration.renderPdf(assetMunicipalityId, template, toTemplateParameters(decision, request.getAdditionalParameters())))));
			partyAssetsIntegration.activateAsset(assetMunicipalityId, assetId);
		} catch (final NonRetryableException e) {
			throw new NonRetryableException(removeDraftAsset(assetMunicipalityId, assetId, e), e);
		} catch (final RuntimeException e) {
			throw Problem.valueOf(BAD_GATEWAY, removeDraftAsset(assetMunicipalityId, assetId, e));
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
