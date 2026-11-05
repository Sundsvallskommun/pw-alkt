package se.sundsvall.alkt.util;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;

/** Reads the text of a value in a JSON parameter. */
public final class JsonText {

	private JsonText() {}

	/**
	 * The text of a string, number or boolean, without surrounding blanks. Empty for null, a blank, an object or a list.
	 */
	public static Optional<String> text(final JsonNode node) {
		return Optional.ofNullable(node)
			.filter(JsonNode::isValueNode)
			.filter(value -> !value.isNull())
			.map(JsonNode::asString)
			.map(String::strip)
			.filter(StringUtils::isNotBlank);
	}
}
