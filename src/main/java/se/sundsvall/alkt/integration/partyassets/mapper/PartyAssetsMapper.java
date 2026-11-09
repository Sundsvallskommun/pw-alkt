package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.service.model.AssetType;
import se.sundsvall.alkt.service.model.PermitSchema;
import se.sundsvall.alkt.util.ByteArrayMultipartFile;
import tools.jackson.databind.JsonNode;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static generated.se.sundsvall.partyassets.Status.EXPIRED;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecidedOn;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toFirstDay;

public final class PartyAssetsMapper {

	static final String ATTACHMENT_PART_NAME = "attachment";
	private static final String CERTIFICATE_FILE_NAME = "tillstandsbevis.pdf";
	private static final String CERTIFICATE_CATEGORY = "Tillståndsbevis";
	static final String ORIGIN = "SUPPORTMANAGEMENT";

	private PartyAssetsMapper() {}

	/** An asset is written by its JSON parameter alone, without additional parameters. */
	public static AssetCreateRequest toAssetCreateRequest(final Decision decision, final String partyId, final AssetType assetType, final AssetJsonParameter parameter) {
		return new AssetCreateRequest()
			.assetId(decision.getId())
			.status(DRAFT)
			.origin(ORIGIN)
			.partyId(partyId)
			.type(assetType.getValue())
			.issued(toFirstDay(decision).orElse(null))
			.validTo(decision.getValidTo())
			.title(decision.getTitle())
			.jsonParameters(List.of(parameter));
	}

	/**
	 * The change of a permit: the validTo of the decision, unless it gives none, and the JSON parameter of the permit
	 * replaced. party-assets replaces the whole list, so the other JSON parameters of the asset are sent as they are. Any
	 * additional parameters are removed, as the JSON parameter holds the permit.
	 */
	public static AssetUpdateRequest toPermitUpdateRequest(final Asset current, final Decision decision, final AssetJsonParameter permit) {
		final var others = Optional.ofNullable(current.getJsonParameters()).orElse(emptyList()).stream()
			.filter(parameter -> !permit.getKey().equals(parameter.getKey()));
		final var update = new AssetUpdateRequest()
			.validTo(decision.getValidTo())
			.jsonParameters(Stream.concat(others, Stream.of(permit)).toList());
		if (!Optional.ofNullable(current.getAdditionalParameters()).orElse(emptyMap()).isEmpty()) {
			update.additionalParameters(emptyMap());
		}
		return update;
	}

	public static AssetJsonParameter toPermitParameter(final PermitSchema schema, final String schemaId, final JsonNode value) {
		return new AssetJsonParameter()
			.key(schema.getName())
			.schemaId(schemaId)
			.value(value);
	}

	public static Optional<AssetJsonParameter> findPermitParameter(final Asset asset, final PermitSchema schema) {
		return Optional.ofNullable(asset.getJsonParameters()).orElse(emptyList()).stream()
			.filter(parameter -> schema.getName().equals(parameter.getKey()))
			.findFirst();
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

	/** An attachment is the certificate of its asset by the category the certificate is attached in. */
	public static boolean isCertificate(final AssetAttachment attachment) {
		return CERTIFICATE_CATEGORY.equals(attachment.getCategory());
	}
}
