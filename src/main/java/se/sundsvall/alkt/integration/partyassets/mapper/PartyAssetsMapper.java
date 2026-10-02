package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.ByteArrayMultipartFile;
import se.sundsvall.dept44.support.Relation;
import se.sundsvall.dept44.support.Relation.ResourceIdentifier;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

public final class PartyAssetsMapper {

	static final String ATTACHMENT_PART_NAME = "attachment";
	static final String CERTIFICATE_FILE_NAME = "tillstandsbevis.pdf";
	static final String CERTIFICATE_CATEGORY = "Tillståndsbevis";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	static final String ORIGIN = "SUPPORTMANAGEMENT";
	static final String PARAMETER_ERRAND_ID = "errandId";
	static final String PARAMETER_LEGAL_BASIS = "legalBasis";
	static final String PARAMETER_DELEGATION_REFERENCE = "delegationReference";
	// Must match the placeholder of the conditions in the certificate template.
	static final String PARAMETER_CONDITIONS = "conditions";
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
	 * The parameters of the decision go on top of those the asset has, so a change need only carry what it changes. The
	 * asset keeps its validTo unless the decision gives one.
	 */
	public static AssetUpdateRequest toAssetUpdateRequest(final Asset current, final Decision decision, final String errandId) {
		final var parameters = new LinkedHashMap<>(Optional.ofNullable(current.getAdditionalParameters()).orElse(emptyMap()));
		parameters.putAll(toAdditionalParameters(decision, errandId));
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
		parameters.put(PARAMETER_ERRAND_ID, errandId);
		Optional.ofNullable(decision.getLegalBasis()).ifPresent(value -> parameters.put(PARAMETER_LEGAL_BASIS, value));
		Optional.ofNullable(decision.getDelegationReference()).ifPresent(value -> parameters.put(PARAMETER_DELEGATION_REFERENCE, value));
		// Why: the parameters of the decision go in last, so one entered on the decision wins over a key of our own with the
		// same
		// name.
		Optional.ofNullable(decision.getParameters()).orElse(emptyList())
			.forEach(parameter -> {
				final var value = toValue(parameter);
				if (isNotBlank(value)) {
					parameters.put(parameter.getKey(), value);
				}
			});
		// Why: a change carries the conditions of the permit over to its certificate, so they are kept on the asset. They go
		// in after the parameters so the terms win, as they do in the certificate.
		Optional.of(toConditions(decision)).filter(StringUtils::isNotBlank).ifPresent(value -> parameters.put(PARAMETER_CONDITIONS, value));
		return parameters;
	}

	// Same order and joining as the conditions TemplatingMapper renders into the certificate.
	private static String toConditions(final Decision decision) {
		return Optional.ofNullable(decision.getTerms()).orElse(emptyList()).stream()
			.sorted(Comparator.comparing(DecisionTerm::getSortOrder, nullsLast(Comparator.naturalOrder())))
			.map(DecisionTerm::getText)
			.filter(StringUtils::isNotBlank)
			.collect(joining("\n"));
	}

	private static String toValue(final Parameter parameter) {
		return Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream()
			.filter(StringUtils::isNotBlank)
			.collect(joining(", "));
	}
}
