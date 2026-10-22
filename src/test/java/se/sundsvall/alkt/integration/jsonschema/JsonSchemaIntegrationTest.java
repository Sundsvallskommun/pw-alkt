package se.sundsvall.alkt.integration.jsonschema;

import generated.se.sundsvall.jsonschema.JsonSchema;
import generated.se.sundsvall.jsonschema.UiSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.ThrowableProblem;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class JsonSchemaIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String SCHEMA_ID = "2281_aot_alcohol_folkol_sales_notification_0.11";

	@Mock
	private JsonSchemaClient jsonSchemaClientMock;

	@InjectMocks
	private JsonSchemaIntegration jsonSchemaIntegration;

	@Test
	void getSchemaAnswersWithTheValueOfTheSchemaOfThatId() {
		final var value = JsonMapper.builder().build().readTree("{\"title\":\"Försäljning av folköl\",\"type\":\"object\"}");
		when(jsonSchemaClientMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok(new JsonSchema().value(value)));

		assertThat(jsonSchemaIntegration.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).isEqualTo(value);

		verify(jsonSchemaClientMock).getSchema(MUNICIPALITY_ID, SCHEMA_ID);
	}

	@Test
	void getUiSchemaAnswersWithTheValueOfTheUiSchemaOfThatId() {
		final var value = JsonMapper.builder().build().readTree("{\"ui:order\":[\"a\",\"b\"]}");
		when(jsonSchemaClientMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok(new UiSchema().value(value)));

		assertThat(jsonSchemaIntegration.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).isEqualTo(value);

		verify(jsonSchemaClientMock).getUiSchema(MUNICIPALITY_ID, SCHEMA_ID);
	}

	@Test
	void getSchemaFailsWithoutRetryForASchemaThatDoesNotExist() {
		when(jsonSchemaClientMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenThrow(new ClientProblem(NOT_FOUND, "Not found"));

		assertThatThrownBy(() -> jsonSchemaIntegration.getSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(SCHEMA_ID)
			.hasMessageContaining("does not exist");
	}

	@Test
	void getUiSchemaFailsWithoutRetryForAUiSchemaThatDoesNotExist() {
		when(jsonSchemaClientMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenThrow(new ClientProblem(NOT_FOUND, "Not found"));

		assertThatThrownBy(() -> jsonSchemaIntegration.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(NonRetryableException.class)
			.hasMessageContaining(SCHEMA_ID)
			.hasMessageContaining("does not exist");
	}

	@Test
	void getSchemaLetsOtherFailuresThroughToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "json-schema is down");
		when(jsonSchemaClientMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenThrow(problem);

		assertThatThrownBy(() -> jsonSchemaIntegration.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).isSameAs(problem);
	}

	@Test
	void getUiSchemaLetsOtherFailuresThroughToBeRetried() {
		final var problem = new ClientProblem(BAD_GATEWAY, "json-schema is down");
		when(jsonSchemaClientMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenThrow(problem);

		assertThatThrownBy(() -> jsonSchemaIntegration.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).isSameAs(problem);
	}

	@Test
	void getSchemaFailsToBeRetriedForAnAnswerWithoutBody() {
		when(jsonSchemaClientMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok().build());

		assertThatThrownBy(() -> jsonSchemaIntegration.getSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(ThrowableProblem.class)
			.isNotInstanceOf(NonRetryableException.class)
			.hasMessageContaining(SCHEMA_ID)
			.hasMessageContaining("without content");
	}

	@Test
	void getSchemaFailsToBeRetriedForASchemaWithoutValue() {
		when(jsonSchemaClientMock.getSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok(new JsonSchema()));

		assertThatThrownBy(() -> jsonSchemaIntegration.getSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(ThrowableProblem.class)
			.hasMessageContaining("without content");
	}

	@Test
	void getUiSchemaFailsToBeRetriedForAnAnswerWithoutBody() {
		when(jsonSchemaClientMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok().build());

		assertThatThrownBy(() -> jsonSchemaIntegration.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(ThrowableProblem.class)
			.hasMessageContaining(SCHEMA_ID)
			.hasMessageContaining("without content");
	}

	@Test
	void getUiSchemaFailsToBeRetriedForAUiSchemaWithoutValue() {
		when(jsonSchemaClientMock.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID)).thenReturn(ResponseEntity.ok(new UiSchema()));

		assertThatThrownBy(() -> jsonSchemaIntegration.getUiSchema(MUNICIPALITY_ID, SCHEMA_ID))
			.isInstanceOf(ThrowableProblem.class)
			.hasMessageContaining("without content");
	}
}
