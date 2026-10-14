package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.configuration.ActionErrandProperties;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.alkt.service.model.ActionErrandResult;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ActionErrandMapper.toActionErrand;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandRelation;

@Service
public class ActionErrandService {

	private static final String RELATION_TYPE_LINK = "LINK";
	private static final String NO_DEFICIENCIES_MESSAGE = "Errand '%s' has no deficiencies, no action errand created";
	private static final String NO_ASSIGNED_USER_MESSAGE = "Errand '%s' has no assigned user to report the action errand, no action errand created";

	private final SupportManagementIntegration supportManagementIntegration;
	private final ActionErrandProperties properties;

	ActionErrandService(final SupportManagementIntegration supportManagementIntegration, final ActionErrandProperties properties) {
		this.supportManagementIntegration = supportManagementIntegration;
		this.properties = properties;
	}

	/**
	 * A rerun finds the action errand an earlier attempt created by its tag, and creates no second one. Refused when the
	 * inspection has no deficiencies, since an action errand without any has nothing to remedy, or no assigned case worker
	 * to report it.
	 */
	public ActionErrandResult createActionErrand(final String municipalityId, final String namespace, final String errandId) {
		return supportManagementIntegration.findErrandIdByExternalTag(municipalityId, namespace, EXTERNAL_TAG_INSPECTION_ERRAND_ID, errandId)
			.map(ActionErrandResult::created)
			.orElseGet(() -> createFromInspection(municipalityId, namespace, errandId));
	}

	private ActionErrandResult createFromInspection(final String municipalityId, final String namespace, final String errandId) {
		final var inspection = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		final var actionErrand = toActionErrand(inspection, errandId, properties.labelId());
		if (actionErrand.getMeasures().isEmpty()) {
			return ActionErrandResult.refused(NO_DEFICIENCIES_MESSAGE.formatted(errandId));
		}
		requirePermitHolder(actionErrand, errandId);
		if (isBlank(actionErrand.getReporterUserId())) {
			return ActionErrandResult.refused(NO_ASSIGNED_USER_MESSAGE.formatted(errandId));
		}
		return ActionErrandResult.created(supportManagementIntegration.createErrand(municipalityId, namespace, toErrandRelation(RELATION_TYPE_LINK, errandId, namespace), actionErrand));
	}

	// Why: a rerun finds a created action errand by its tag and creates no new one, so an action errand created without a
	// permit holder would stay without one.
	private static void requirePermitHolder(final Errand actionErrand, final String errandId) {
		if (actionErrand.getStakeholders().isEmpty()) {
			throw new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId));
		}
	}
}
