package se.sundsvall.alkt.integration.supportmanagement.model;

import generated.se.sundsvall.supportmanagement.Message;
import java.util.List;

// Why: not the generated PageMessage. Its spec says sort is an object, but Support Management answers with an array, so
// only the fields we read are mapped.
public record MessagePage(List<Message> content, Boolean last) {
}
