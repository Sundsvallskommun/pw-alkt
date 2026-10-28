package se.sundsvall.alkt.integration.jsonschema.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.json-schema")
public record JsonSchemaProperties(int connectTimeout, int readTimeout) {
}
