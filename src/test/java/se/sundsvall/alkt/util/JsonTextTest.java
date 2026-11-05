package se.sundsvall.alkt.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTextTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void textTakesAValueWithoutItsSurroundingBlanks() {
		assertThat(JsonText.text(JSON.readTree("\" Runt Hörnet AB \""))).contains("Runt Hörnet AB");
		assertThat(JsonText.text(JSON.readTree("40"))).contains("40");
		assertThat(JsonText.text(JSON.readTree("true"))).contains("true");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"null", "\"\"", "\" \"", "{}", "[\"Runt Hörnet AB\"]"
	})
	void textIsEmptyForNullABlankAnObjectOrAList(final String json) {
		assertThat(JsonText.text(JSON.readTree(json))).isEmpty();
	}

	@Test
	void textIsEmptyForAMissingNode() {
		assertThat(JsonText.text(JSON.readTree("{}").path("name"))).isEmpty();
		assertThat(JsonText.text(null)).isEmpty();
	}
}
