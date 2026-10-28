package se.sundsvall.alkt.integration.templating.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void eachFieldOfASectionIsSentWithItsTypeAndOnlyItsOwnParts() {
		final var section = new Section("Verksamhet", List.of(
			new ValueField("Namn", "Kiosken"),
			new ListField("Drycker", List.of("Öl", "Vin")),
			new HtmlField("Beskrivning", "<p>Hej</p>"),
			new GroupField("Kontakt", List.of(new GroupEntry("E-post", "a@example.com"))),
			new TableField("Bilagor", List.of("Filnamn", "Typ"), List.of(TableField.Row.of("fullmakt.pdf", "Fullmakt")))));

		assertThat(JSON.readTree(JSON.writeValueAsString(section))).isEqualTo(JSON.readTree("""
			{"title": "Verksamhet", "fields": [
			  {"type": "value", "label": "Namn", "value": "Kiosken"},
			  {"type": "list", "label": "Drycker", "values": ["Öl", "Vin"]},
			  {"type": "html", "label": "Beskrivning", "html": "<p>Hej</p>"},
			  {"type": "group", "label": "Kontakt", "fields": [{"label": "E-post", "value": "a@example.com"}]},
			  {"type": "table", "label": "Bilagor", "columns": ["Filnamn", "Typ"], "rows": [["fullmakt.pdf", "Fullmakt"]]}]}
			"""));
	}

	@Test
	void aValueThatIsNullIsLeftOut() {
		assertThat(JSON.readTree(JSON.writeValueAsString(new ValueField("Ärendenummer", null)))).isEqualTo(JSON.readTree("""
			{"type": "value", "label": "Ärendenummer"}
			"""));
	}
}
