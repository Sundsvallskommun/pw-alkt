package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import se.sundsvall.alkt.integration.partyassets.model.AssetFile;
import se.sundsvall.alkt.integration.partyassets.model.ByteArrayMultipartFile;

import static generated.se.sundsvall.partyassets.Status.DRAFT;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

public final class PartyAssetsMapper {

	static final String ATTACHMENT_PART_NAME = "attachment";
	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	static final String ORIGIN = "SUPPORTMANAGEMENT";
	static final String PARAMETER_ERRAND_ID = "errandId";
	static final String PARAMETER_LEGAL_BASIS = "legalBasis";
	static final String PARAMETER_DELEGATION_REFERENCE = "delegationReference";

	private PartyAssetsMapper() {}

	public static AssetCreateRequest toAssetCreateRequest(final Decision decision, final String errandId, final String partyId) {
		return new AssetCreateRequest()
			.assetId(decision.getId())
			.status(DRAFT)
			.origin(ORIGIN)
			.partyId(partyId)
			.type(decision.getType())
			.issued(toIssued(decision))
			.validTo(decision.getValidTo())
			.description(decision.getTitle())
			.additionalParameters(toAdditionalParameters(decision, errandId));
	}

	public static AssetFile toAssetFile(final ErrandAttachment attachment, final byte[] content) {
		return new AssetFile(
			new ByteArrayMultipartFile(ATTACHMENT_PART_NAME, attachment.getFileName(), attachment.getMimeType(), content),
			Optional.ofNullable(attachment.getPurpose())
				.map(ErrandAttachmentPurpose::getName)
				.orElse(null));
	}

	public static Optional<String> toPartyId(final Errand errand) {
		return Optional.ofNullable(errand.getStakeholders())
			.orElseGet(List::of)
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
		return parameters;
	}
}
