package se.sundsvall.alkt.integration.supportmanagement.model;

import generated.se.sundsvall.supportmanagement.Message;
import java.util.List;

public record MessagePage(List<Message> content, Boolean last) {
}
