package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static se.sundsvall.alkt.Constants.EXTERNAL_TAG_INSPECTION_ERRAND_ID;
import static se.sundsvall.alkt.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

public final class ActionErrandMapper {

	private ActionErrandMapper() {}

	/**
	 * The deficiencies found by the inspection are its measures; they follow without the fields Support Management sets.
	 */
	public static Errand toActionErrand(final Errand inspection, final String category, final String type) {
		return new Errand()
			.title(inspection.getTitle())
			.description(inspection.getDescription())
			.classification(new Classification().category(category).type(type))
			.stakeholders(Optional.ofNullable(inspection.getStakeholders()).orElse(emptyList()).stream()
				.filter(stakeholder -> STAKEHOLDER_ROLE_PERMIT_HOLDER.equals(stakeholder.getRole()))
				.toList())
			.measures(Optional.ofNullable(inspection.getMeasures()).orElse(emptyList()).stream()
				.map(ActionErrandMapper::toMeasure)
				.toList())
			.externalTags(Set.of(new ExternalTag().key(EXTERNAL_TAG_INSPECTION_ERRAND_ID).value(inspection.getId())));
	}

	// Format given by Support Management; the target part is left empty since it is the errand being created.
	public static String toReferredFrom(final String errandId, final String namespace) {
		return "%s|%s;errand;support-management;%s|".formatted(RELATION_TYPE_LINK, errandId, namespace);
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
