package se.sundsvall.alkt.integration.operaton.mapper;

import generated.se.sundsvall.operaton.VariableValueDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
		final var errandId = UUID.randomUUID().toString();
		final var requestId = UUID.randomUUID().toString();

		// Act
		final generated.se.sundsvall.operaton.StartProcessInstanceDto result;
		try (MockedStatic<RequestId> requestIdMock = mockStatic(RequestId.class)) {
			requestIdMock.when(RequestId::get).thenReturn(requestId);
			result = OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, errandId);
		}

		// Assert
		assertThat(result.getBusinessKey()).isEqualTo(errandId);
		assertThat(result.getVariables())
			.hasSize(4)
			.extractingByKeys("municipalityId", "namespace", "errandId", "requestId")
			.extracting(VariableValueDto::getType, VariableValueDto::getValue)
			.containsExactly(
				tuple(ValueType.STRING.getName(), municipalityId),
				tuple(ValueType.STRING.getName(), namespace),
				tuple(ValueType.STRING.getName(), errandId),
				tuple(ValueType.STRING.getName(), requestId));
	}

	@Test
	void toCorrelationMessageDto() {
		// Arrange
		final var messageName = "errandUpdated";
		final var errandId = UUID.randomUUID().toString();
		final var tenantId = "ALKT";

		// Act
		final var result = OperatonMapper.toCorrelationMessageDto(messageName, errandId, tenantId);

		// Assert
		assertThat(result.getMessageName()).isEqualTo(messageName);
		assertThat(result.getBusinessKey()).isEqualTo(errandId);
		assertThat(result.getTenantId()).isEqualTo(tenantId);
		// Correlating to more than one execution would fan the message out across parallel branches instead of failing
		assertThat(result.getAll()).isFalse();
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
	void toOperatonTimestamp() {
		final var timestamp = OffsetDateTime.of(2026, 9, 14, 8, 5, 3, 21_000_000, ZoneOffset.ofHours(2));

		// Operaton takes a zone offset without a colon, which is not what OffsetDateTime.toString() gives
		assertThat(OperatonMapper.toOperatonTimestamp(timestamp)).isEqualTo("2026-09-14T08:05:03.021+0200");
	}

	@Test
	void toProcessDefinitionKeyIn() {
		assertThat(OperatonMapper.toProcessDefinitionKeyIn(List.of("alcohol-serving", "supervision"))).isEqualTo("alcohol-serving,supervision");
		assertThat(OperatonMapper.toProcessDefinitionKeyIn(List.of())).isEmpty();
	}
}
