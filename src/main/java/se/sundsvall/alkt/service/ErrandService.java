package se.sundsvall.alkt.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

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
}
