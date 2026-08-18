package se.sundsvall.alkt.service;

import generated.se.sundsvall.operaton.PatchVariablesDto;
import java.util.Map;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.springframework.stereotype.Service;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ANSOKAN;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_UPDATE_AVAILABLE;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;

@Service
public class ProcessService {

	private final OperatonClient operatonClient;

	ProcessService(OperatonClient operatonClient) {
		this.operatonClient = operatonClient;
	}

	public String startProcess(final String municipalityId, final String namespace, final String errandId) {
		return startProcess(PROCESS_KEY_ANSOKAN, municipalityId, namespace, errandId);
	}

	/**
	 * Starts the process definition matching the provided key in the tenant owned by this service. The upcoming anmalan
	 * and tillsyn processes are started through this method with their own key, so only the caller has to know which
	 * process a request maps to.
	 */
	String startProcess(final String processKey, final String municipalityId, final String namespace, final String errandId) {
		return operatonClient.startProcessWithTenant(processKey, TENANT_ID_ALKT, OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, errandId)).getId();
	}

	public void updateProcess(final String municipalityId, final String namespace, final String processInstanceId) {
		if (operatonClient.getProcessInstance(processInstanceId).isEmpty()) {
			throw Problem.valueOf(NOT_FOUND, "Process instance with ID '%s' does not exist!".formatted(processInstanceId));
		}

		operatonClient.setProcessInstanceVariables(processInstanceId, updateVariables(municipalityId, namespace));
	}

	private PatchVariablesDto updateVariables(final String municipalityId, final String namespace) {
		return OperatonMapper.toPatchVariablesDto(Map.of(
			PROCESS_VARIABLE_MUNICIPALITY_ID, OperatonMapper.toVariableValueDto(ValueType.STRING, municipalityId),
			PROCESS_VARIABLE_NAMESPACE, OperatonMapper.toVariableValueDto(ValueType.STRING, namespace),
			PROCESS_VARIABLE_UPDATE_AVAILABLE, OperatonMapper.toVariableValueDto(ValueType.BOOLEAN, true),
			PROCESS_VARIABLE_REQUEST_ID, OperatonMapper.toVariableValueDto(ValueType.STRING, RequestId.get())));
	}
}
