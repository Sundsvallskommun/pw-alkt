package se.sundsvall.alkt.integration.operaton.mapper;

import generated.se.sundsvall.operaton.PatchVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import generated.se.sundsvall.operaton.VariableValueDto;
import java.util.Map;
import org.camunda.bpm.engine.variable.type.ValueType;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_CASE_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;

public final class OperatonMapper {

	private OperatonMapper() {}

	public static StartProcessInstanceDto toStartProcessInstanceDto(final String municipalityId, final String namespace, final Long caseNumber) {
		return new StartProcessInstanceDto()
			.businessKey(Long.toString(caseNumber))
			.variables(Map.of(
				PROCESS_VARIABLE_MUNICIPALITY_ID, toVariableValueDto(ValueType.STRING, municipalityId),
				PROCESS_VARIABLE_NAMESPACE, toVariableValueDto(ValueType.STRING, namespace),
				PROCESS_VARIABLE_CASE_NUMBER, toVariableValueDto(ValueType.LONG, caseNumber),
				PROCESS_VARIABLE_REQUEST_ID, toVariableValueDto(ValueType.STRING, RequestId.get())));
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
