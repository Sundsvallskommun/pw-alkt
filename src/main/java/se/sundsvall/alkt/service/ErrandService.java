package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.CaseNumberCheck;

import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_EXTERNAL_REFERENCE_NUMBER;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.MUNICIPALITY_ID_TIMRA;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.withParameter;

@Service
public class ErrandService {

	static final String ERRAND_PARAMETER_COMPLETE_AT = "completeAt";

	private final SupportManagementIntegration supportManagementIntegration;
	private final Clock clock;

	ErrandService(final SupportManagementIntegration supportManagementIntegration, final Clock clock) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.clock = clock;
	}

	/**
	 * The day the process reached the step, which is the day the application was complete and the investigation could
	 * start,
	 * also when the step succeeds only on a later retry. Every other parameter is sent back as read, and a change to the
	 * errand since the read makes the step rerun on a fresh one.
	 */
	public LocalDate setCompleteAt(final String municipalityId, final String namespace, final String errandId, final Instant reachedAt) {
		final var completeAt = LocalDate.ofInstant(reachedAt, clock.getZone());
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		supportManagementIntegration.updateErrandParameters(municipalityId, namespace, errandId, errand.getVersion(),
			withParameter(errand.getParameters(), ERRAND_PARAMETER_COMPLETE_AT, completeAt.toString()));
		return completeAt;
	}

	/** The case number comes from the records of Timrå, in externalReferenceNumber. */
	public CaseNumberCheck checkCaseNumber(final String municipalityId, final String namespace, final String errandId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		if (!requiresCaseNumber(errand)) {
			return CaseNumberCheck.NOT_NEEDED;
		}
		return toParameterValues(errand.getParameters()).containsKey(ERRAND_PARAMETER_EXTERNAL_REFERENCE_NUMBER) ? CaseNumberCheck.PRESENT : CaseNumberCheck.MISSING;
	}

	public boolean isCaseNumberRequired(final String municipalityId, final String namespace, final String errandId) {
		return requiresCaseNumber(supportManagementIntegration.getErrand(municipalityId, namespace, errandId));
	}

	/**
	 * Only an errand in Timrå needs a case number. Without a premises municipality that is unknown, so the errand stops.
	 */
	private static boolean requiresCaseNumber(final Errand errand) {
		if (!toParameterValues(errand.getParameters()).containsKey(ERRAND_PARAMETER_PREMISES_MUNICIPALITY_ID)) {
			throw new NonRetryableException("Errand '%s' has no %s".formatted(errand.getId(), ERRAND_PARAMETER_PREMISES_MUNICIPALITY_ID));
		}
		return MUNICIPALITY_ID_TIMRA.equals(toPremisesMunicipalityId(errand));
	}
}
