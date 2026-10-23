package se.sundsvall.alkt.integration.templating.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void aTableIsSentWithItsRowsAsArraysOfTexts() {
		final var table = Field.table("Bilagor", List.of("Filnamn", "Typ"), List.of(Row.of("fullmakt.pdf", "Fullmakt")));

		assertThat(JSON.writeValueAsString(table))
			.isEqualTo("{\"label\":\"Bilagor\",\"type\":\"table\",\"columns\":[\"Filnamn\",\"Typ\"],\"rows\":[[\"fullmakt.pdf\",\"Fullmakt\"]]}");
	}
}
