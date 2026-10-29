package se.sundsvall.alkt.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.CaseNumberCheck;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_TIMRA_CASE_NUMBER;
import static se.sundsvall.alkt.Constants.MUNICIPALITY_ID_TIMRA;
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

	/** Only an errand in Timrå needs a case number, from the records of Timrå in timraCaseNumber. */
	public CaseNumberCheck checkCaseNumber(final String municipalityId, final String namespace, final String errandId) {
		final var errand = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		if (!MUNICIPALITY_ID_TIMRA.equals(toPremisesMunicipalityId(errand))) {
			return CaseNumberCheck.NOT_NEEDED;
		}
		final var present = Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_TIMRA_CASE_NUMBER.equals(parameter.getKey()))
			.flatMap(parameter -> Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream())
			.anyMatch(StringUtils::isNotBlank);
		return present ? CaseNumberCheck.PRESENT : CaseNumberCheck.MISSING;
	}

	public String getPremisesMunicipalityId(final String municipalityId, final String namespace, final String errandId) {
		return toPremisesMunicipalityId(supportManagementIntegration.getErrand(municipalityId, namespace, errandId));
	}
}
