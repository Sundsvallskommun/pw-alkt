package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.Collections.emptyMap;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETERS_OF_THE_PROCESS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_E_CIGARETTE_SALES;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PERMIT_TYPE_TOBACCO_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_ADDITION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_E_CIGARETTE_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CLOSURE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toConditions;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toRemovedParameterKeys;

public final class PartyAssetsMapper {

	static final String ATTACHMENT_PART_NAME = "attachment";
	static final String CERTIFICATE_FILE_NAME = "tillstandsbevis.pdf";
	static final String CERTIFICATE_CATEGORY = "Tillståndsbevis";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	static final String ORIGIN = "SUPPORTMANAGEMENT";

	private static final Map<String, String> PERMIT_TYPES = Map.of(
		PROCESS_KEY_ALCOHOL_SERVING, PERMIT_TYPE_ALCOHOL_SERVING,
		PROCESS_KEY_ALCOHOL_SERVING_CHANGE, PERMIT_TYPE_ALCOHOL_SERVING,
		PROCESS_KEY_ALCOHOL_SERVING_ADDITION, PERMIT_TYPE_ALCOHOL_SERVING,
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES, PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES,
		PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING, PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING,
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING, PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING,
		PROCESS_KEY_TOBACCO_SALES, PERMIT_TYPE_TOBACCO_SALES,
		PROCESS_KEY_TOBACCO_SALES_CHANGE, PERMIT_TYPE_TOBACCO_SALES,
		PROCESS_KEY_TOBACCO_SALES_CLOSURE, PERMIT_TYPE_TOBACCO_SALES,
		PROCESS_KEY_E_CIGARETTE_SALES, PERMIT_TYPE_E_CIGARETTE_SALES);

	private PartyAssetsMapper() {}

	public static String toPermitType(final String processKey) {
		return Optional.ofNullable(processKey)
			.map(PERMIT_TYPES::get)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no permit type, one of %s was expected"
				.formatted(processKey, PERMIT_TYPES.keySet())));
	}

	public static AssetCreateRequest toAssetCreateRequest(final Decision decision, final String errandId, final String partyId, final String permitType) {
		return new AssetCreateRequest()
			.assetId(decision.getId())
			.status(DRAFT)
			.origin(ORIGIN)
			.partyId(partyId)
			.type(permitType)
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

	/**
	 * Ends the permit on the last day the decision gives, else the day it was decided. A last day still to come is left to
	 * party-assets, which expires the permit once it has passed. No status reason, as party-assets refuses one unless
	 * reasons are registered for the status.
	 */
	public static AssetUpdateRequest toAssetClosureRequest(final Decision decision) {
		final var validTo = Optional.ofNullable(decision.getValidTo())
			.or(() -> toDecidedOn(decision))
			.orElseGet(() -> LocalDate.now(SWEDISH_TIME));
		final var request = new AssetUpdateRequest().validTo(validTo);
		if (!validTo.isAfter(LocalDate.now(SWEDISH_TIME))) {
			request.status(EXPIRED);
		}
		return request;
	}

	private static Optional<LocalDate> toDecidedOn(final Decision decision) {
		return Optional.ofNullable(decision.getDecidedAt()).map(decidedAt -> decidedAt.atZoneSameInstant(SWEDISH_TIME).toLocalDate());
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

	private static LocalDate toIssued(final Decision decision) {
		return Optional.ofNullable(decision.getValidFrom())
			.or(() -> toDecidedOn(decision))
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
		parameters.putAll(toParameterValues(decision.getParameters()));
		Optional.of(toConditions(decision)).filter(StringUtils::isNotBlank).ifPresent(value -> parameters.put(PERMIT_PARAMETER_CONDITIONS, value));
		// Why: errandId links the asset to the errand that granted it, which no decision parameter may change.
		parameters.remove(PERMIT_PARAMETER_ERRAND_ID);
		return parameters;
	}
}
