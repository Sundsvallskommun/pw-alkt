package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeName("list")
public record ListField(String label, List<String> values)
	implements
	Field {
}
