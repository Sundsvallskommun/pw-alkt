package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;
import se.sundsvall.dept44.support.Relation;
import se.sundsvall.dept44.support.Relation.ResourceIdentifier;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETERS_OF_THE_PROCESS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toConditions;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toRemovedParameterKeys;

public final class PartyAssetsMapper {

	static final String ATTACHMENT_PART_NAME = "attachment";
	static final String CERTIFICATE_FILE_NAME = "tillstandsbevis.pdf";
	static final String CERTIFICATE_CATEGORY = "Tillståndsbevis";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	static final String ORIGIN = "SUPPORTMANAGEMENT";
	static final String ERRAND_RESOURCE_TYPE = "case";
	static final String ERRAND_SERVICE = "supportmanagement";

	private PartyAssetsMapper() {}

	public static String toSourceReference(final String relationType, final String errandId, final String namespace) {
		return Relation.create(relationType, ResourceIdentifier.create(errandId, ERRAND_RESOURCE_TYPE, ERRAND_SERVICE, namespace), null)
			.toRelationString();
	}

	public static AssetCreateRequest toAssetCreateRequest(final Decision decision, final String errandId, final String partyId) {
		return new AssetCreateRequest()
			.assetId(decision.getId())
			.status(DRAFT)
			.origin(ORIGIN)
			.partyId(partyId)
			.type(decision.getType())
			.issued(toIssued(decision))
			.validTo(decision.getValidTo())
			.title(decision.getTitle())
			.description(decision.getDescription())
			.additionalParameters(toAdditionalParameters(decision, errandId));
	}

	/**
	 * The decision's parameters go on top of the asset's, and one without a value removes the key. validTo and conditions
	 * stay unless the decision gives new ones, and errandId keeps naming the granting errand.
	 */
	public static AssetUpdateRequest toAssetUpdateRequest(final Asset current, final Decision decision) {
		final var parameters = new LinkedHashMap<>(Optional.ofNullable(current.getAdditionalParameters()).orElse(emptyMap()));
		parameters.putAll(toDecisionParameters(decision));
		toRemovedParameterKeys(decision).stream()
			.filter(key -> !PERMIT_PARAMETERS_OF_THE_PROCESS.contains(key))
			.forEach(parameters::remove);
		return new AssetUpdateRequest()
			.validTo(decision.getValidTo())
			.additionalParameters(parameters);
	}

	public static AssetFile toAssetFile(final ErrandAttachment attachment, final byte[] content) {
		return new AssetFile(
			new ByteArrayMultipartFile(ATTACHMENT_PART_NAME, attachment.getFileName(), attachment.getMimeType(), content),
			Optional.ofNullable(attachment.getPurpose())
				.map(ErrandAttachmentPurpose::getDisplayName)
				.orElse(null));
	}

	public static AssetFile toCertificateFile(final byte[] content) {
		return new AssetFile(new ByteArrayMultipartFile(ATTACHMENT_PART_NAME, CERTIFICATE_FILE_NAME, APPLICATION_PDF_VALUE, content), CERTIFICATE_CATEGORY);
	}

	public static Optional<String> toPartyId(final Errand errand) {
		return Optional.ofNullable(errand.getStakeholders())
			.orElse(emptyList())
			.stream()
			.filter(stakeholder -> STAKEHOLDER_ROLE_PERMIT_HOLDER.equals(stakeholder.getRole()))
			.map(Stakeholder::getExternalId)
			.filter(Objects::nonNull)
			.findFirst();
	}

	private static LocalDate toIssued(final Decision decision) {
		return Optional.ofNullable(decision.getValidFrom())
			.or(() -> Optional.ofNullable(decision.getDecidedAt()).map(decidedAt -> decidedAt.atZoneSameInstant(SWEDISH_TIME).toLocalDate()))
			.orElse(null);
	}

	private static Map<String, String> toAdditionalParameters(final Decision decision, final String errandId) {
		final var parameters = new LinkedHashMap<String, String>();
		parameters.put(PERMIT_PARAMETER_ERRAND_ID, errandId);
		parameters.putAll(toDecisionParameters(decision));
		return parameters;
	}

	private static Map<String, String> toDecisionParameters(final Decision decision) {
		final var parameters = new LinkedHashMap<String, String>();
		Optional.ofNullable(decision.getLegalBasis()).ifPresent(value -> parameters.put(PERMIT_PARAMETER_LEGAL_BASIS, value));
		Optional.ofNullable(decision.getDelegationReference()).ifPresent(value -> parameters.put(PERMIT_PARAMETER_DELEGATION_REFERENCE, value));
		// Why: the decision's parameters go in after our own keys, so they win over them, except the conditions, which the
		// terms carry as on the certificate.
		parameters.putAll(toParameterValues(decision));
		Optional.of(toConditions(decision)).filter(StringUtils::isNotBlank).ifPresent(value -> parameters.put(PERMIT_PARAMETER_CONDITIONS, value));
		// Why: errandId links the asset to the errand that granted it, which no decision parameter may change.
		parameters.remove(PERMIT_PARAMETER_ERRAND_ID);
		return parameters;
	}
}
