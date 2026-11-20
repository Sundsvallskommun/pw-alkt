package se.sundsvall.alkt.integration.jsonschema;

import generated.se.sundsvall.jsonschema.JsonSchema;
import generated.se.sundsvall.jsonschema.UiSchema;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.sundsvall.alkt.integration.jsonschema.configuration.JsonSchemaConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.jsonschema.configuration.JsonSchemaConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.json-schema.url}", configuration = JsonSchemaConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface JsonSchemaClient {

	@GetMapping(path = "/{municipalityId}/schemas/{id}", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<JsonSchema> getSchema(
		@PathVariable String municipalityId,
		@PathVariable String id);

	@GetMapping(path = "/{municipalityId}/schemas/{id}/ui-schema", produces = APPLICATION_JSON_VALUE)
	ResponseEntity<UiSchema> getUiSchema(
		@PathVariable String municipalityId,
		@PathVariable String id);
}
