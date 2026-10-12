package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.Collections.emptyMap;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_CONDITIONS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_DELEGATION_REFERENCE;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.alkt.Constants.PERMIT_PARAMETER_RESTAURANT_NUMBER;
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
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecidedOn;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toFirstDay;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toRemovedParameterKeys;

public final class PartyAssetsMapper {

	public static final String PERMIT_TYPE_ALCOHOL_SERVING = "AlcoholServingPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES = "LowAlcoholBeerSalesPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING = "LowAlcoholBeerServingPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING = "LowAlcoholBeerSalesAndServingPermit";
	public static final String PERMIT_TYPE_TOBACCO_SALES = "TobaccoSalesPermit";
	public static final String PERMIT_TYPE_E_CIGARETTE_SALES = "ECigaretteSalesPermit";

	// Set by pw-alkt from the errand and the decision, so a decision cannot remove them.
	private static final Set<String> PERMIT_PARAMETERS_OF_THE_PROCESS = Set.of(PERMIT_PARAMETER_ERRAND_ID, PERMIT_PARAMETER_LEGAL_BASIS,
		PERMIT_PARAMETER_DELEGATION_REFERENCE, PERMIT_PARAMETER_CONDITIONS, PERMIT_PARAMETER_RESTAURANT_NUMBER);

	static final String ATTACHMENT_PART_NAME = "attachment";
	static final String CERTIFICATE_FILE_NAME = "tillstandsbevis.pdf";
	static final String CERTIFICATE_CATEGORY = "Tillståndsbevis";
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

	public static AssetCreateRequest toAssetCreateRequest(final Decision decision, final String errandId, final String partyId, final String permitType,
		final String restaurantNumber) {
		return new AssetCreateRequest()
			.assetId(decision.getId())
			.status(DRAFT)
			.origin(ORIGIN)
			.partyId(partyId)
			.type(permitType)
			.issued(toFirstDay(decision).orElse(null))
			.validTo(decision.getValidTo())
			.title(decision.getTitle())
			.description(decision.getDescription())
			.additionalParameters(toAdditionalParameters(decision, errandId, restaurantNumber));
	}

	/**
	 * The decision's parameters go on top of the asset's, and one without a value removes the key. validTo and conditions
	 * stay unless the decision gives new ones, errandId keeps naming the granting errand and premisesRestaurantNumber keeps
	 * the number licensed business holds.
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
	 * Ends the permit on the last day the decision gives, else the day it was decided, but never later than the permit
	 * already ends. A last day that has not passed is left to party-assets, which expires the permit the day after. No
	 * status reason, as party-assets refuses one unless reasons are registered for the status.
	 */
	public static AssetUpdateRequest toAssetClosureRequest(final Asset current, final Decision decision, final LocalDate today) {
		final var lastDay = Optional.ofNullable(decision.getValidTo())
			.or(() -> toDecidedOn(decision))
			.orElse(today);
		final var validTo = Optional.ofNullable(current.getValidTo())
			.filter(currentLastDay -> currentLastDay.isBefore(lastDay))
			.orElse(lastDay);
		final var request = new AssetUpdateRequest().validTo(validTo);
		if (validTo.isBefore(today)) {
			request.status(EXPIRED);
		}
		return request;
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

	private static Map<String, String> toAdditionalParameters(final Decision decision, final String errandId, final String restaurantNumber) {
		final var parameters = new LinkedHashMap<String, String>();
		parameters.put(PERMIT_PARAMETER_ERRAND_ID, errandId);
		parameters.putAll(toDecisionParameters(decision));
		Optional.ofNullable(restaurantNumber).filter(StringUtils::isNotBlank).ifPresent(value -> parameters.put(PERMIT_PARAMETER_RESTAURANT_NUMBER, value));
		return parameters;
	}

	private static Map<String, String> toDecisionParameters(final Decision decision) {
		final var parameters = new LinkedHashMap<String, String>();
		Optional.ofNullable(decision.getLegalBasis()).ifPresent(value -> parameters.put(PERMIT_PARAMETER_LEGAL_BASIS, value));
		Optional.ofNullable(decision.getDelegationReference()).ifPresent(value -> parameters.put(PERMIT_PARAMETER_DELEGATION_REFERENCE, value));
		// Why: the decision's parameters go in after legalBasis and delegationReference, so they win over them. The
		// conditions come from the decision's terms only.
		parameters.putAll(toParameterValues(decision.getParameters()));
		parameters.remove(PERMIT_PARAMETER_CONDITIONS);
		Optional.of(toConditions(decision)).filter(StringUtils::isNotBlank).ifPresent(value -> parameters.put(PERMIT_PARAMETER_CONDITIONS, value));
		// Why: set by the process, so a decision cannot change them.
		parameters.remove(PERMIT_PARAMETER_ERRAND_ID);
		parameters.remove(PERMIT_PARAMETER_RESTAURANT_NUMBER);
		return parameters;
	}
}
