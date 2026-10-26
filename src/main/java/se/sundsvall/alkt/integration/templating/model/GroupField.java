package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeName("group")
public record GroupField(String label, List<GroupEntry> fields)
	implements
	Field {
}
