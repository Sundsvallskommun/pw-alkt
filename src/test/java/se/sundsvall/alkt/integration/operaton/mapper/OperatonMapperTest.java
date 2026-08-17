package se.sundsvall.alkt.integration.operaton.mapper;

import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mockStatic;

class OperatonMapperTest {

	@Test
	void toStartProcessInstanceDto() {
		// Arrange
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var caseNumber = 123L;
		final var requestId = UUID.randomUUID().toString();

		// Act
		final generated.se.sundsvall.operaton.StartProcessInstanceDto result;
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(requestId);
			result = OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, caseNumber);
		}

		// Assert
		assertThat(result.getBusinessKey()).isEqualTo("123");
		assertThat(result.getVariables())
			.hasSize(4)
			.extractingByKeys("municipalityId", "namespace", "caseNumber", "requestId")
			.extracting(VariableValueDto::getType, VariableValueDto::getValue)
			.containsExactly(
				tuple(ValueType.STRING.getName(), municipalityId),
				tuple(ValueType.STRING.getName(), namespace),
				tuple(ValueType.LONG.getName(), caseNumber),
				tuple(ValueType.STRING.getName(), requestId));
	}

	@Test
	void toVariableValueDto() {
		// Act
		final var result = OperatonMapper.toVariableValueDto(ValueType.BOOLEAN, true);

		// Assert
		assertThat(result.getType()).isEqualTo(ValueType.BOOLEAN.getName());
		assertThat(result.getValue()).isEqualTo(true);
	}

	@Test
	void toPatchVariablesDto() {
		// Arrange
		final var variable = OperatonMapper.toVariableValueDto(ValueType.STRING, "value");

		// Act
		final var result = OperatonMapper.toPatchVariablesDto(Map.of("key", variable));

		// Assert
		assertThat(result.getModifications()).containsExactly(Map.entry("key", variable));
	}
}
