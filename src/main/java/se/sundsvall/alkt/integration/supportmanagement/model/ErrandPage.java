package se.sundsvall.alkt.integration.supportmanagement.model;

import generated.se.sundsvall.supportmanagement.Errand;
import java.util.List;

// Not the generated PageErrand: Support Management answers with sort as an array where its spec says object.
public record ErrandPage(List<Errand> content) {
}
