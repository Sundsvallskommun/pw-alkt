package se.sundsvall.alkt.service;

import generated.se.sundsvall.supportmanagement.Errand;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementIntegration;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;
import static se.sundsvall.alkt.Constants.NO_PERMIT_HOLDER_MESSAGE;
import static se.sundsvall.alkt.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ActionErrandMapper.toActionErrand;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toErrandRelation;

@Service
public class ActionErrandService {

	private final SupportManagementIntegration supportManagementIntegration;

	ActionErrandService(final SupportManagementIntegration supportManagementIntegration) {
		this.supportManagementIntegration = supportManagementIntegration;
	}

	/**
	 * A rerun finds the action errand an earlier attempt created by its tag, and creates no second one. Empty when the
	 * inspection has no deficiencies, since an action errand without any has nothing to remedy.
	 */
	public Optional<String> createActionErrand(final String municipalityId, final String namespace, final String errandId, final String category, final String type) {
		if (isAnyBlank(category, type)) {
			throw new NonRetryableException("The step has no category or type for the action errand, see the input parameters in the bpmn schema");
		}

		return supportManagementIntegration.findErrandIdByExternalTag(municipalityId, namespace, EXTERNAL_TAG_INSPECTION_ERRAND_ID, errandId)
			.or(() -> createFromInspection(municipalityId, namespace, errandId, category, type));
	}

	private Optional<String> createFromInspection(final String municipalityId, final String namespace, final String errandId, final String category, final String type) {
		final var inspection = supportManagementIntegration.getErrand(municipalityId, namespace, errandId);
		return Optional.of(toActionErrand(inspection, errandId, category, type))
			.filter(actionErrand -> !actionErrand.getMeasures().isEmpty())
			.map(actionErrand -> requirePermitHolder(actionErrand, errandId))
			.map(actionErrand -> supportManagementIntegration.createErrand(municipalityId, namespace, toErrandRelation(RELATION_TYPE_LINK, errandId, namespace), actionErrand));
	}

	// Why: a rerun finds a created action errand by its tag and creates no new one, so an action errand created without a
	// permit holder would stay without one.
	private static Errand requirePermitHolder(final Errand actionErrand, final String errandId) {
		if (actionErrand.getStakeholders().isEmpty()) {
			throw new NonRetryableException(NO_PERMIT_HOLDER_MESSAGE.formatted(errandId));
		}
		return actionErrand;
	}
}
