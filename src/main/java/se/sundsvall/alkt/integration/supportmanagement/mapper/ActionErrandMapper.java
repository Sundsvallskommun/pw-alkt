package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static generated.se.sundsvall.supportmanagement.Priority.MEDIUM;
import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_PARAMETER_PBI;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_PARAMETER_PBI_ROLE;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.isPermitHolder;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.isPersonOfSignificantInfluence;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toActiveMeasures;

public final class ActionErrandMapper {

	private static final String MEASURE_TYPE_ACTION_POINT = "EXTERNAL";
	private static final String TITLE = "Åtgärdsärende";
	private static final String STATUS_NEW = "NEW";
	private static final String CHANNEL_WEB_UI = "WEB_UI";
	private static final String PBI_PARAMETER_PREFIX = "PBI_";
	private static final String STAKEHOLDER_PARAMETER_PBI_SOURCE = "PBI_SOURCE";
	private static final Set<String> PBI_PARAMETERS_CARRIED_OVER = Set.of(STAKEHOLDER_PARAMETER_PBI, STAKEHOLDER_PARAMETER_PBI_ROLE, STAKEHOLDER_PARAMETER_PBI_SOURCE);

	private ActionErrandMapper() {}

	/** The deficiencies are the active action points of the inspection, without the fields Support Management sets. */
	public static Errand toActionErrand(final Errand inspection, final String errandId, final String labelId) {
		return new Errand()
			.title(TITLE)
			.status(STATUS_NEW)
			.priority(MEDIUM)
			.channel(CHANNEL_WEB_UI)
			.reporterUserId(inspection.getAssignedUserId())
			.assignedUserId(inspection.getAssignedUserId())
			.description(inspection.getDescription())
			.labels(List.of(new ErrandLabel().id(labelId)))
			.parameters(toPremisesMunicipality(inspection))
			.stakeholders(toStakeholders(inspection))
			.measures(toActiveMeasures(inspection.getMeasures(), MEASURE_TYPE_ACTION_POINT).stream()
				.map(ActionErrandMapper::toMeasure)
				.toList())
			.externalTags(Set.of(new ExternalTag().key(EXTERNAL_TAG_INSPECTION_ERRAND_ID).value(errandId)))
			.phases(null)
			.activeNotifications(null)
			.actions(null);
	}

	/**
	 * The permit holder and the persons of significant influence. Of the PBI parameters only the marking, role and source
	 * follow.
	 */
	private static List<Stakeholder> toStakeholders(final Errand inspection) {
		return Optional.ofNullable(inspection.getStakeholders()).orElse(emptyList()).stream()
			.filter(stakeholder -> isPermitHolder(stakeholder) || isPersonOfSignificantInfluence(stakeholder))
			.map(ActionErrandMapper::toActionErrandStakeholder)
			.toList();
	}

	private static Stakeholder toActionErrandStakeholder(final Stakeholder stakeholder) {
		return Optional.of(stakeholder)
			.filter(SupportManagementMapper::isPermitHolder)
			.map(ActionErrandMapper::toPermitHolder)
			.orElseGet(() -> toPersonOfSignificantInfluence(stakeholder));
	}

	private static Stakeholder toPermitHolder(final Stakeholder stakeholder) {
		return new Stakeholder()
			.externalId(stakeholder.getExternalId())
			.externalIdType(stakeholder.getExternalIdType())
			.role(stakeholder.getRole())
			.city(stakeholder.getCity())
			.organizationName(stakeholder.getOrganizationName())
			.firstName(stakeholder.getFirstName())
			.lastName(stakeholder.getLastName())
			.address(stakeholder.getAddress())
			.careOf(stakeholder.getCareOf())
			.zipCode(stakeholder.getZipCode())
			.country(stakeholder.getCountry())
			.contactChannels(stakeholder.getContactChannels())
			.parameters(Optional.ofNullable(stakeholder.getParameters()).orElse(emptyList()).stream()
				.filter(parameter -> !isPbiParameterLeftBehind(parameter))
				.toList());
	}

	private static Stakeholder toPersonOfSignificantInfluence(final Stakeholder stakeholder) {
		return new Stakeholder()
			.externalId(stakeholder.getExternalId())
			.externalIdType(stakeholder.getExternalIdType())
			.role(stakeholder.getRole())
			.firstName(stakeholder.getFirstName())
			.lastName(stakeholder.getLastName())
			.organizationName(stakeholder.getOrganizationName())
			.parameters(Optional.ofNullable(stakeholder.getParameters()).orElse(emptyList()).stream()
				.filter(parameter -> PBI_PARAMETERS_CARRIED_OVER.contains(parameter.getKey()))
				.toList());
	}

	private static boolean isPbiParameterLeftBehind(final Parameter parameter) {
		return Optional.ofNullable(parameter.getKey())
			.filter(key -> key.startsWith(PBI_PARAMETER_PREFIX))
			.filter(key -> !PBI_PARAMETERS_CARRIED_OVER.contains(key))
			.isPresent();
	}

	private static List<Parameter> toPremisesMunicipality(final Errand inspection) {
		return Optional.ofNullable(inspection.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> ERRAND_PARAMETER_PREMISES_MUNICIPALITY_ID.equals(parameter.getKey()))
			.toList();
	}

	private static Measure toMeasure(final Measure measure) {
		return new Measure()
			.type(measure.getType())
			.title(measure.getTitle())
			.description(measure.getDescription())
			.goal(measure.getGoal())
			.dueAt(measure.getDueAt())
			.addedByUser(measure.getAddedByUser())
			.addedByRole(measure.getAddedByRole());
	}
}
