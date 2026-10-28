package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("html")
public record HtmlField(String label, String html)
	implements
	Field {
}
