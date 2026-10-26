package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("value")
public record ValueField(String label, String value)
	implements
	Field {
}
