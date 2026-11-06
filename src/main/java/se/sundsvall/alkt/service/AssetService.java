package se.sundsvall.alkt.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.PermitJsonProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.VersionedAsset;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.integration.templating.TemplatingIntegration;
import se.sundsvall.alkt.service.model.AssetType;
import se.sundsvall.alkt.service.model.PermitSchema;
import se.sundsvall.dept44.common.validators.annotation.impl.ValidUuidConstraintValidator;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.JsonNode;

import static generated.se.sundsvall.partyassets.Status.ACTIVE;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_ASSET_ID;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.findPermitParameter;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetClosureRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toCertificateFile;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitCreateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitParameter;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitUpdateRequest;
import static se.sundsvall.alkt.integration.partyassets.mapper.PermitJsonMapper.toChangedPermit;
import static se.sundsvall.alkt.integration.partyassets.mapper.PermitJsonMapper.toGrantedPermit;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findAttachmentId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseFileName;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseNumber;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toConditionList;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPartyId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.templating.mapper.PermitCertificateMapper.toCertificateParameters;
import static se.sundsvall.alkt.util.FailureDescription.describe;

@Service
public class AssetService {

	private static final ValidUuidConstraintValidator UUID_VALIDATOR = new ValidUuidConstraintValidator();

	private final SupportManagementIntegration supportManagementIntegration;
	private final PartyAssetsIntegration partyAssetsIntegration;
	private final TemplatingIntegration templatingIntegration;
	private final PermitJsonProperties permitJsonProperties;
	private final Clock clock;

	AssetService(final SupportManagementIntegration supportManagementIntegration, final PartyAssetsIntegration partyAssetsIntegration, final TemplatingIntegration templatingIntegration,
		final PermitJsonProperties permitJsonProperties, final Clock clock) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.partyAssetsIntegration = partyAssetsIntegration;
		this.templatingIntegration = templatingIntegration;
		this.permitJsonProperties = permitJsonProperties;
		this.clock = clock;
	}

	/**
	 * A permit is built from the JSON parameter of its decision and gets a certificate rendered from it, and without a
	 * restaurant number it carries none. The certificate is kept on the errand as well, also by a rerun that finds the
	 * permit already made. A notification gets the parameters of its decision as additional parameters and no certificate.
	 * A decision with a validTo must end after today, as party-assets refuses to activate the asset otherwise.
	 */
	public String findOrCreateAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType, final String restaurantNumber) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		requireId(decision, errandId);

		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var partyId = requirePartyId(errand, errandId);

		return partyAssetsIntegration.findAsset(municipalityId, partyId, decision.getId())
			.map(asset -> {
				requireAssetType(asset, assetType);
				if (assetType.getCertificateTemplate().isPresent()) {
					keepCertificateOnErrand(municipalityId, namespace, errandId, errand, decision, () -> partyAssetsIntegration.findCertificate(municipalityId, asset.getId()));
				}
				return asset.getId();
			})
			.orElseGet(() -> {
				requireNotEnded(decision, errandId);
				return assetType.getPermitSchema().isPresent()
					? createPermit(municipalityId, namespace, errandId, errand, decision, assetType, restaurantNumber)
					: createAsset(municipalityId, namespace, errandId, decision, toAssetCreateRequest(decision, errandId, partyId, assetType), Optional.empty());
			});
	}

	/**
	 * Changes the permit the errand names: the JSON parameter of the change decision goes on top of the one of the permit,
	 * and the certificate is rendered anew from the result and kept on the errand as well. party-assets keeps the earlier
	 * content as a revision.
	 */
	public String updateAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		requireNotPassed(decision, errandId);
		requireId(decision, errandId);
		final var schema = assetType.getPermitSchema()
			.orElseThrow(() -> new NonRetryableException("An asset of type '%s' is not changed by a decision".formatted(assetType.getValue())));
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var versioned = getPermitOfTheErrand(municipalityId, errand, errandId, assetType);
		final var asset = versioned.asset();
		final var assetId = asset.getId();
		requireActive(asset);
		final var current = requirePermitParameter(asset, schema);

		final var permit = toChangedPermit(schema, current.getValue(), getDecisionJson(municipalityId, namespace, errandId, decision, schema), toCaseRegistrationNumber(errand, errandId),
			toConditionList(decision));
		final var parameter = toPermitParameter(schema, toSchemaId(municipalityId, schema), permit);
		final var update = toPermitUpdateRequest(asset, decision, parameter);
		final var certificate = renderCertificate(assetType, schema, permit, decision, errand);

		if (changes(update, asset, current, parameter)) {
			partyAssetsIntegration.updateAsset(municipalityId, assetId, versioned.version(), update);
		}
		certificate.ifPresent(content -> {
			partyAssetsIntegration.replaceCertificate(municipalityId, assetId, toCertificateFile(content));
			keepCertificateOnErrand(municipalityId, namespace, errandId, errand, decision, () -> Optional.of(content));
		});
		return assetId;
	}

	/**
	 * Checks the permit the errand names before the case worker handles the errand, and answers with its id. A permit that
	 * has already ended passes, as the closure then has nothing to do.
	 */
	public String checkPermit(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var asset = getPermitOfTheErrand(municipalityId, supportManagementIntegration.getErrand(municipalityId, namespace, errandId), errandId, assetType).asset();
		if (asset.getStatus() != EXPIRED) {
			requireActive(asset);
		}
		return asset.getId();
	}

	/** Ends the permit the errand names. The permit certificate stays as it is. */
	public String closeAsset(final String municipalityId, final String namespace, final String errandId, final AssetType assetType) {
		final var decision = getApprovingDecision(municipalityId, namespace, errandId);
		final var versioned = getPermitOfTheErrand(municipalityId, supportManagementIntegration.getErrand(municipalityId, namespace, errandId), errandId, assetType);
		final var asset = versioned.asset();
		final var closure = toAssetClosureRequest(asset, decision, LocalDate.now(clock));

		if (!isClosed(asset, closure)) {
			requireActive(asset);
			requireNotBeforeIssued(asset, closure, errandId);
			partyAssetsIntegration.updateAsset(municipalityId, asset.getId(), versioned.version(), closure);
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

	/**
	 * The permit the errand names, which the customer chose, so a fault in it is not something a retry fixes. A permit of
	 * an asset type with a schema must hold its JSON parameter, as a change goes on top of it.
	 */
	public VersionedAsset getPermitToChange(final String municipalityId, final Errand errand, final String errandId, final AssetType assetType) {
		final var versioned = getPermitOfTheErrand(municipalityId, errand, errandId, assetType);
		requireActive(versioned.asset());
		assetType.getPermitSchema().ifPresent(schema -> requirePermitParameter(versioned.asset(), schema));
		return versioned;
	}

	private static AssetJsonParameter requirePermitParameter(final Asset asset, final PermitSchema schema) {
		return findPermitParameter(asset, schema)
			.orElseThrow(() -> new NonRetryableException("Asset '%s' has no JSON parameter '%s' to change".formatted(asset.getId(), schema.getName())));
	}

	private VersionedAsset getPermitOfTheErrand(final String municipalityId, final Errand errand, final String errandId, final AssetType assetType) {
		final var assetId = toAssetId(errand)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' names no asset to change".formatted(errandId)));
		if (!UUID_VALIDATOR.isValid(assetId)) {
			throw new NonRetryableException("Errand '%s' names asset '%s', which is not an asset id".formatted(errandId, assetId));
		}
		final var partyId = requirePartyId(errand, errandId);
		final var versioned = partyAssetsIntegration.getAsset(municipalityId, assetId);
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

	private static void requireId(final Decision decision, final String errandId) {
		if (isBlank(decision.getId())) {
			throw Problem.valueOf(UNPROCESSABLE_CONTENT, "Decision of errand '%s' has no id to identify its asset by".formatted(errandId));
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

	private JsonNode getDecisionJson(final String municipalityId, final String namespace, final String errandId, final Decision decision, final PermitSchema schema) {
		return supportManagementIntegration.getDecisionJson(municipalityId, namespace, errandId, decision.getId(), permitJsonProperties.of(schema).decisionKey());
	}

	private String toSchemaId(final String municipalityId, final PermitSchema schema) {
		return schema.toSchemaId(municipalityId, permitJsonProperties.of(schema).schemaVersion());
	}

	/** The case number of the errand is its own errand number, also in Timrå, whose number the decision holds. */
	private static String toCaseRegistrationNumber(final Errand errand, final String errandId) {
		return Optional.ofNullable(errand.getErrandNumber())
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> new NonRetryableException("Errand '%s' has no errand number to give the permit as its case number".formatted(errandId)));
	}

	private static Optional<String> toAssetId(final Errand errand) {
		return Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_ASSET_ID.equals(parameter.getKey()))
			.flatMap(parameter -> Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream())
			.filter(StringUtils::isNotBlank)
			.findFirst();
	}

	private static boolean changes(final AssetUpdateRequest update, final Asset asset, final AssetJsonParameter current, final AssetJsonParameter permit) {
		return update.getAdditionalParameters() != null
			|| !Objects.equals(current.getSchemaId(), permit.getSchemaId())
			|| !Objects.equals(current.getValue(), permit.getValue())
			|| (update.getValidTo() != null && !update.getValidTo().equals(asset.getValidTo()));
	}

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

	/**
	 * The permit and its certificate are made before the draft is created, so a permit or a certificate that cannot be made
	 * leaves no draft. party-assets validates the JSON parameter of the permit against its schema as the draft is created.
	 * The certificate goes on the errand once the permit is active, so an errand never shows a certificate of a permit
	 * that was not made, and a copy that cannot be uploaded leaves the permit in place for the rerun to find.
	 */
	private String createPermit(final String municipalityId, final String namespace, final String errandId, final Errand errand, final Decision decision,
		final AssetType assetType, final String restaurantNumber) {
		final var schema = assetType.getPermitSchema().orElseThrow();
		final var permit = toGrantedPermit(schema, getDecisionJson(municipalityId, namespace, errandId, decision, schema), errandId, toCaseRegistrationNumber(errand, errandId),
			restaurantNumber, toConditionList(decision));
		final var certificate = renderCertificate(assetType, schema, permit, decision, errand);
		final var request = toPermitCreateRequest(decision, requirePartyId(errand, errandId), assetType, toPermitParameter(schema, toSchemaId(municipalityId, schema), permit));
		final var assetId = createAsset(municipalityId, namespace, errandId, decision, request, certificate.map(PartyAssetsMapper::toCertificateFile));
		certificate.ifPresent(content -> keepCertificateOnErrand(municipalityId, namespace, errandId, errand, decision, () -> Optional.of(content)));
		return assetId;
	}

	private static String requirePartyId(final Errand errand, final String errandId) {
		return toPartyId(errand)
			.orElseThrow(() -> new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId)));
	}

	/** Rendered in the municipality of the premises, with the case number the errand has there. */
	private Optional<byte[]> renderCertificate(final AssetType assetType, final PermitSchema schema, final JsonNode permit, final Decision decision, final Errand errand) {
		return assetType.getCertificateTemplate()
			.map(template -> templatingIntegration.renderPdf(toPremisesMunicipalityId(errand), template, toCertificateParameters(schema, permit, decision, toCaseNumber(errand))));
	}

	/**
	 * Uploads the certificate to the errand as an attachment of its own, named for the case number the certificate shows.
	 * A rerun finds the copy an earlier attempt uploaded by its file name and by it being uploaded after the decision was
	 * created, and asks for the certificate only when the errand has no copy.
	 */
	private void keepCertificateOnErrand(final String municipalityId, final String namespace, final String errandId, final Errand errand, final Decision decision,
		final Supplier<Optional<byte[]>> certificate) {
		final var fileName = toCaseFileName("Tillståndsbevis", errand);
		// Support Management stamps the decision and the attachment by the same clock, and a copy from before the decision
		// is the one of an earlier decision of the errand
		final var decisionCreated = Optional.ofNullable(decision.getCreated()).orElse(OffsetDateTime.MIN);
		if (findAttachmentId(supportManagementIntegration.getAttachments(municipalityId, namespace, errandId), fileName, decisionCreated).isEmpty()) {
			certificate.get().ifPresent(content -> supportManagementIntegration.createPdfAttachment(municipalityId, namespace, errandId, fileName, content));
		}
	}

	private String createAsset(final String municipalityId, final String namespace, final String errandId, final Decision decision, final AssetCreateRequest request,
		final Optional<AssetFile> certificate) {
		final var assetId = partyAssetsIntegration.createDraftAsset(municipalityId, namespace, errandId, request);

		try {
			Optional.ofNullable(decision.getAttachments()).orElse(emptyList())
				.forEach(attachment -> partyAssetsIntegration.addAttachmentToDraft(municipalityId, assetId,
					toAssetFile(attachment, supportManagementIntegration.getAttachment(municipalityId, namespace, errandId, attachment.getId()))));
			certificate.ifPresent(file -> partyAssetsIntegration.addAttachmentToDraft(municipalityId, assetId, file));
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
