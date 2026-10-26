package se.sundsvall.alkt.integration.templating.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

/**
 * A field of the template alkt.form-pdf. Its type, from the type name of the record, tells the template how to draw it.
 */
@JsonInclude(NON_NULL)
@JsonTypeInfo(use = NAME, property = "type")
public interface Field {

	String label();
}
