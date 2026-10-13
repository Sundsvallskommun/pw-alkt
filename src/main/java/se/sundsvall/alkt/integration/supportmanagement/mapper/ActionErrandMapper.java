package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static generated.se.sundsvall.supportmanagement.Priority.MEDIUM;
import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;

public final class ActionErrandMapper {

	private static final String MEASURE_STATUS_ACTIVE = "ACTIVE";
	private static final String TITLE = "Åtgärdsärende";
	private static final String STATUS_NEW = "NEW";

	private ActionErrandMapper() {}

	/**
	 * The deficiencies found by the inspection are its active measures, since a draft, completed or cancelled one is
	 * nothing to remedy. They follow without the fields Support Management sets.
	 */
	public static Errand toActionErrand(final Errand inspection, final String errandId, final String labelId) {
		return new Errand()
			.title(TITLE)
			.status(STATUS_NEW)
			.priority(MEDIUM)
			.reporterUserId(inspection.getAssignedUserId())
			.description(inspection.getDescription())
			.classification(inspection.getClassification())
			.labels(List.of(new ErrandLabel().id(labelId)))
			.stakeholders(Optional.ofNullable(inspection.getStakeholders()).orElse(emptyList()).stream()
				.filter(SupportManagementMapper::isPermitHolder)
				.toList())
			.measures(Optional.ofNullable(inspection.getMeasures()).orElse(emptyList()).stream()
				.filter(measure -> MEASURE_STATUS_ACTIVE.equals(measure.getStatus()))
				.map(ActionErrandMapper::toMeasure)
				.toList())
			.externalTags(Set.of(new ExternalTag().key(EXTERNAL_TAG_INSPECTION_ERRAND_ID).value(errandId)));
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
