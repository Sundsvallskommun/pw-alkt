package se.sundsvall.alkt.integration.partyassets.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.model.PermitSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Builds the JSON parameter of a permit from the JSON parameter of the decision that grants or changes it. A field
 * given
 * as null is left out, as the schema of a permit has no null.
 */
public final class PermitJsonMapper {

	private static final String ERRAND_ID = "errandId";
	private static final String CASE_REGISTRATION_NUMBER = "caseRegistrationNumber";
	private static final String CONDITIONS = "conditions";
	private static final String DECISION = "decision";
	private static final String MIGRATION = "migration";

	private static final List<List<String>> FIELDS_A_CHANGE_KEEPS = List.of(List.of(ERRAND_ID), List.of(CASE_REGISTRATION_NUMBER), List.of(MIGRATION));

	private PermitJsonMapper() {}

	/**
	 * The permit a decision grants: the JSON parameter of the decision without the fields only a decision has. The fields
	 * the process owns are set by it, whatever the decision says of them: the granting errand, its case number, also as the
	 * one of the decision, the restaurant number the process assigned, or none, and the terms of the decision as
	 * conditions, or none. Fails without retry when the JSON parameter of the decision, or a field the process sets a value
	 * in, is not a JSON object.
	 */
	public static ObjectNode toGrantedPermit(final PermitSchema schema, final JsonNode decisionJson, final String errandId, final String caseRegistrationNumber,
		final String restaurantNumber, final List<String> conditions) {
		final var permit = copyOfObject(decisionJson);
		schema.getDecisionOnlyFields().forEach(field -> remove(permit, field));
		permit.put(ERRAND_ID, errandId);
		permit.put(CASE_REGISTRATION_NUMBER, caseRegistrationNumber);
		objectAt(permit, List.of(DECISION)).put(CASE_REGISTRATION_NUMBER, caseRegistrationNumber);
		schema.getRestaurantNumberField().ifPresent(field -> {
			if (isBlank(restaurantNumber)) {
				remove(permit, field);
			} else {
				set(permit, field, JsonNodeFactory.instance.stringNode(restaurantNumber));
			}
		});
		setConditions(permit, conditions);
		removeNulls(permit);
		return permit;
	}

	/**
	 * The permit after a change: the JSON parameter of the change decision on top of the one of the permit. A field of the
	 * change replaces the one of the permit, an object is merged field by field, a list is replaced whole, a field the
	 * change leaves out stays and a field the change gives as null is removed.
	 * <p>
	 * errandId, caseRegistrationNumber and migration never change, nor does the restaurant number, which the process
	 * assigned. The decision is the one of the change, with the case number of the change errand, and the terms of the
	 * change replace the conditions when it has any. Fails without retry when either JSON parameter, or a field the
	 * process sets a value in, is not a JSON object.
	 */
	public static ObjectNode toChangedPermit(final PermitSchema schema, final JsonNode permitJson, final JsonNode changeJson, final String caseRegistrationNumber,
		final List<String> conditions) {
		final var permit = copyOfObject(permitJson);
		final var change = copyOfObject(changeJson);
		schema.getDecisionOnlyFields().forEach(field -> remove(change, field));
		change.remove(CONDITIONS);
		final var changedDecision = change.remove(DECISION);

		final var kept = new LinkedHashMap<List<String>, JsonNode>();
		// Copies, as the merge changes the objects of the permit in place
		Stream.concat(FIELDS_A_CHANGE_KEEPS.stream(), schema.getRestaurantNumberField().stream())
			.forEach(field -> kept.put(field, Optional.ofNullable(get(permit, field)).map(JsonNode::deepCopy).orElse(null)));

		merge(permit, change);

		kept.forEach((field, value) -> {
			if (value == null) {
				remove(permit, field);
			} else {
				set(permit, field, value);
			}
		});
		permit.set(DECISION, toDecisionOfChange(changedDecision, caseRegistrationNumber));
		if (!conditions.isEmpty()) {
			setConditions(permit, conditions);
		}
		removeNulls(permit);
		return permit;
	}

	private static ObjectNode toDecisionOfChange(final JsonNode changedDecision, final String caseRegistrationNumber) {
		if (changedDecision == null || changedDecision.isNull()) {
			return JsonNodeFactory.instance.objectNode().put(CASE_REGISTRATION_NUMBER, caseRegistrationNumber);
		}
		if (changedDecision instanceof final ObjectNode decision) {
			return decision.put(CASE_REGISTRATION_NUMBER, caseRegistrationNumber);
		}
		throw notAnObject(DECISION, changedDecision);
	}

	/** RFC 7386: an object is merged field by field, null removes the field, anything else replaces it. */
	private static void merge(final ObjectNode target, final ObjectNode patch) {
		patch.properties().forEach(property -> {
			final var name = property.getKey();
			final var value = property.getValue();
			if (value.isNull()) {
				target.remove(name);
			} else if (value instanceof final ObjectNode patchObject) {
				final var targetObject = target.get(name) instanceof final ObjectNode existing ? existing : target.putObject(name);
				merge(targetObject, patchObject);
			} else {
				target.set(name, value);
			}
		});
	}

	private static void removeNulls(final JsonNode node) {
		if (node instanceof final ObjectNode object) {
			new ArrayList<>(object.propertyNames()).forEach(name -> {
				if (object.get(name).isNull()) {
					object.remove(name);
				} else {
					removeNulls(object.get(name));
				}
			});
		} else if (node instanceof final ArrayNode array) {
			for (var index = array.size() - 1; index >= 0; index--) {
				if (array.get(index).isNull()) {
					array.remove(index);
				} else {
					removeNulls(array.get(index));
				}
			}
		}
	}

	private static void setConditions(final ObjectNode permit, final List<String> conditions) {
		if (conditions.isEmpty()) {
			permit.remove(CONDITIONS);
			return;
		}
		final var array = permit.putArray(CONDITIONS);
		conditions.forEach(array::add);
	}

	private static ObjectNode copyOfObject(final JsonNode node) {
		if (node instanceof final ObjectNode object) {
			return object.deepCopy();
		}
		throw new NonRetryableException("A JSON parameter of a permit must be a JSON object, but was %s".formatted(node == null ? "missing" : node.getNodeType()));
	}

	private static JsonNode get(final ObjectNode root, final List<String> field) {
		JsonNode node = root;
		for (final var name : field) {
			if (!(node instanceof final ObjectNode object)) {
				return null;
			}
			node = object.get(name);
		}
		return node;
	}

	private static void remove(final ObjectNode root, final List<String> field) {
		if (get(root, field.subList(0, field.size() - 1)) instanceof final ObjectNode parent) {
			parent.remove(field.getLast());
		}
	}

	private static void set(final ObjectNode root, final List<String> field, final JsonNode value) {
		objectAt(root, field.subList(0, field.size() - 1)).set(field.getLast(), value);
	}

	private static ObjectNode objectAt(final ObjectNode root, final List<String> field) {
		var object = root;
		for (final var name : field) {
			final var child = object.get(name);
			if (child == null || child.isNull()) {
				object = object.putObject(name);
			} else if (child instanceof final ObjectNode childObject) {
				object = childObject;
			} else {
				throw notAnObject(name, child);
			}
		}
		return object;
	}

	private static NonRetryableException notAnObject(final String field, final JsonNode value) {
		return new NonRetryableException("The field '%s' of the JSON parameter of a permit must be a JSON object, but was %s".formatted(field, value.getNodeType()));
	}
}
