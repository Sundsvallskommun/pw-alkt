package se.sundsvall.alkt.integration.supportmanagement.model;

import generated.se.sundsvall.supportmanagement.Errand;
import java.util.List;

public record ErrandPage(List<Errand> content) {
}
