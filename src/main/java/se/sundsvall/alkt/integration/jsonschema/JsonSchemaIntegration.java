package se.sundsvall.alkt.integration.jsonschema;

import generated.se.sundsvall.jsonschema.JsonSchema;
import generated.se.sundsvall.jsonschema.UiSchema;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.JsonNode;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.util.FailureDescription.describe;

@Component
public class JsonSchemaIntegration {

	private final JsonSchemaClient jsonSchemaClient;

	JsonSchemaIntegration(final JsonSchemaClient jsonSchemaClient) {
		this.jsonSchemaClient = jsonSchemaClient;
	}

	public JsonNode getSchema(final String municipalityId, final String schemaId) {
		return Optional.ofNullable(failWithoutRetryWhenMissing(() -> jsonSchemaClient.getSchema(municipalityId, schemaId), "Schema", schemaId).getBody())
			.map(JsonSchema::getValue)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Schema '%s' came back without content".formatted(schemaId)));
	}

	public JsonNode getUiSchema(final String municipalityId, final String schemaId) {
		return Optional.ofNullable(failWithoutRetryWhenMissing(() -> jsonSchemaClient.getUiSchema(municipalityId, schemaId), "UI schema of schema", schemaId).getBody())
			.map(UiSchema::getValue)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "UI schema of schema '%s' came back without content".formatted(schemaId)));
	}

	// Why: a schema that does not exist will not appear on a retry.
	private static <T> ResponseEntity<T> failWithoutRetryWhenMissing(final Supplier<ResponseEntity<T>> request, final String what, final String schemaId) {
		try {
			return request.get();
		} catch (final ClientProblem e) {
			if (NOT_FOUND.equals(e.getStatus())) {
				throw new NonRetryableException("%s '%s' does not exist: %s".formatted(what, schemaId, describe(e)), e);
			}
			throw e;
		}
	}
}
