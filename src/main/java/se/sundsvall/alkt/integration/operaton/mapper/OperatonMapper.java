package se.sundsvall.alkt.integration.operaton.mapper;

import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.PatchVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.Map;
import org.camunda.bpm.engine.variable.type.ValueType;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;

public final class OperatonMapper {

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

	/**
	 * Builds the correlation body that wakes the instance driving the given errand. {@code all} is set to false here and
	 * nowhere else: correlating to several executions at once would silently fan a message out across parallel branches,
	 * which the process models are not allowed to have. With the flag off, Operaton answers 400 instead and the breach
	 * becomes visible.
	 */
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

	public static PatchVariablesDto toPatchVariablesDto(final Map<String, VariableValueDto> variablesToUpdate) {
		return new PatchVariablesDto()
			.modifications(variablesToUpdate);
	}
}
