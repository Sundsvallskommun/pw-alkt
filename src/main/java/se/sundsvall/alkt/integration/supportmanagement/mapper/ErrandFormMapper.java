package se.sundsvall.alkt.integration.supportmanagement.mapper;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;

/** Reads answers in the form JSON of an errand, so the decision basis and the asset agree on what the form says. */
public final class ErrandFormMapper {

	private static final String YES = "YES";

	private ErrandFormMapper() {}

	/** A text or number answer, stripped, or empty when it is blank or not a value. */
	public static Optional<String> findText(final JsonNode answer) {
		return Optional.of(answer)
			.filter(node -> node.isString() || node.isNumber())
			.map(node -> node.asString().strip())
			.filter(StringUtils::isNotEmpty);
	}

	public static boolean isYes(final JsonNode answer) {
		return findText(answer).filter(YES::equals).isPresent();
	}
}
