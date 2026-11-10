package se.sundsvall.alkt.integration.supportmanagement.mapper;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ErrandFormMapper.findText;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.ErrandFormMapper.isYes;

class ErrandFormMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void findTextStripsATextAnswer() {
		assertThat(findText(json("\"  Kafé Solsidan \""))).contains("Kafé Solsidan");
	}

	@Test
	void findTextTakesANumberAnswerAsText() {
		assertThat(findText(json("85231"))).contains("85231");
	}

	@Test
	void findTextFindsNothingInABlankMissingOrObjectAnswer() {
		assertThat(findText(json("\"   \""))).isEmpty();
		assertThat(findText(json("{}").path("premisesName"))).isEmpty();
		assertThat(findText(json("{ \"street\": \"Storgatan 33\" }"))).isEmpty();
	}

	@Test
	void isYesOnlyForTheAnswerYes() {
		assertThat(isYes(json("\"YES\""))).isTrue();
		assertThat(isYes(json("\"NO\""))).isFalse();
		assertThat(isYes(json("{}").path("premisesAddressSameAsCompany"))).isFalse();
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
