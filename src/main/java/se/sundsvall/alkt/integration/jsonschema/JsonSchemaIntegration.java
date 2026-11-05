package se.sundsvall.alkt.integration.jsonschema;

import generated.se.sundsvall.jsonschema.JsonSchema;
import generated.se.sundsvall.jsonschema.UiSchema;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.JsonNode;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.util.RemoteAnswers.failWithoutRetryOn;

@Component
public class JsonSchemaIntegration {

	private final JsonSchemaClient jsonSchemaClient;

	JsonSchemaIntegration(final JsonSchemaClient jsonSchemaClient) {
		this.jsonSchemaClient = jsonSchemaClient;
	}

	public JsonNode getSchema(final String municipalityId, final String schemaId) {
		return Optional.ofNullable(failWithoutRetryOn(NOT_FOUND, "Schema '%s' does not exist".formatted(schemaId), () -> jsonSchemaClient.getSchema(municipalityId, schemaId)).getBody())
			.map(JsonSchema::getValue)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Schema '%s' came back without content".formatted(schemaId)));
	}

	public JsonNode getUiSchema(final String municipalityId, final String schemaId) {
		return Optional.ofNullable(failWithoutRetryOn(NOT_FOUND, "UI schema of schema '%s' does not exist".formatted(schemaId), () -> jsonSchemaClient.getUiSchema(municipalityId, schemaId)).getBody())
			.map(UiSchema::getValue)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "UI schema of schema '%s' came back without content".formatted(schemaId)));
	}

}
