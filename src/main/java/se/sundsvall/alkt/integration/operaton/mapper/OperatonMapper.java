package se.sundsvall.alkt.integration.operaton.mapper;

import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import org.camunda.bpm.engine.variable.type.ValueType;
import se.sundsvall.dept44.requestid.RequestId;

import static java.util.stream.Collectors.joining;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;

public final class OperatonMapper {

	private static final DateTimeFormatter OPERATON_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

	private OperatonMapper() {}

	public static StartProcessInstanceDto toStartProcessInstanceDto(final String municipalityId, final String namespace, final String errandId) {
		return new StartProcessInstanceDto()
			.businessKey(errandId)
			.variables(Map.of(
				PROCESS_VARIABLE_MUNICIPALITY_ID, toVariableValueDto(ValueType.STRING, municipalityId),
				PROCESS_VARIABLE_NAMESPACE, toVariableValueDto(ValueType.STRING, namespace),
				PROCESS_VARIABLE_ERRAND_ID, toVariableValueDto(ValueType.STRING, errandId),
				PROCESS_VARIABLE_REQUEST_ID, toVariableValueDto(ValueType.STRING, RequestId.get())));
	}

	public static CorrelationMessageDto toCorrelationMessageDto(final String messageName, final String errandId, final String tenantId) {
		return new CorrelationMessageDto()
			.messageName(messageName)
			.businessKey(errandId)
			.tenantId(tenantId)
			.all(false);
	}

	public static VariableValueDto toVariableValueDto(final ValueType valueType, final Object value) {
		return new VariableValueDto()
			.type(valueType.getName())
			.value(value);
	}

	/** The format Operaton's REST API takes for timestamps, e.g. 2013-01-01T00:00:00.000+0200. */
	public static String toOperatonTimestamp(final OffsetDateTime timestamp) {
		return OPERATON_TIMESTAMP.format(timestamp);
	}

	/** The *In query parameters take a comma separated list. Sorted, so the same set always gives the same query. */
	public static String toProcessDefinitionKeyIn(final Collection<String> processKeys) {
		return processKeys.stream().sorted().collect(joining(","));
	}
}
