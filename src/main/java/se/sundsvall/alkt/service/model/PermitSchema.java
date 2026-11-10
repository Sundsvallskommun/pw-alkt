package se.sundsvall.alkt.service.model;

import java.util.List;
import java.util.Optional;

/**
 * The AoT schemas of the JSON parameter of an asset in party-assets, one per area. The schema of the decision on a
 * permit extends the one of its area, so the decision carries the permit nearly as it is.
 */
public enum PermitSchema {

	ALCOHOL("aot_alcohol_party_asset", List.of(List.of("premises", "newRestaurantNumber"), List.of("decision", "clauses")), List.of("premises", "restaurantNumber")),
	TOBACCO("aot_tobacco_party_asset", List.of(List.of("decision", "clauses")), null);

	private final String name;
	private final List<List<String>> decisionOnlyFields;
	private final List<String> restaurantNumberField;

	PermitSchema(final String name, final List<List<String>> decisionOnlyFields, final List<String> restaurantNumberField) {
		this.name = name;
		this.decisionOnlyFields = decisionOnlyFields;
		this.restaurantNumberField = restaurantNumberField;
	}

	/** The name of the schema in json-schema, which is also the key of the JSON parameter on the asset. */
	public String getName() {
		return name;
	}

	/** The id of the schema in json-schema: the municipality, the name and the version. */
	public String toSchemaId(final String municipalityId, final String version) {
		return "%s_%s_%s".formatted(municipalityId, name, version);
	}

	/** The fields of the decision schema that the permit schema does not have, each as its path of property names. */
	public List<List<String>> getDecisionOnlyFields() {
		return decisionOnlyFields;
	}

	/** The path of the restaurant number, which only a place that serves alcohol has. */
	public Optional<List<String>> getRestaurantNumberField() {
		return Optional.ofNullable(restaurantNumberField);
	}
}
