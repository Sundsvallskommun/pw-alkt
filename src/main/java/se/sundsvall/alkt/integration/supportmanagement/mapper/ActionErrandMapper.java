package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;

public final class ActionErrandMapper {

	private static final String MEASURE_STATUS_ACTIVE = "ACTIVE";

	private ActionErrandMapper() {}

	/**
	 * The deficiencies found by the inspection are its active measures, since a draft, completed or cancelled one is
	 * nothing to remedy. They follow without the fields Support Management sets.
	 */
	public static Errand toActionErrand(final Errand inspection, final String errandId, final String category, final String type) {
		return new Errand()
			.title(inspection.getTitle())
			.description(inspection.getDescription())
			.classification(new Classification().category(category).type(type))
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
