package se.sundsvall.alkt.integration.operaton;

import generated.se.sundsvall.operaton.ProcessInstanceDto;
import java.util.List;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.integration.operaton.mapper.OperatonMapper;

@Component
public class OperatonIntegration {

	private final OperatonClient operatonClient;

	OperatonIntegration(final OperatonClient operatonClient) {
		this.operatonClient = operatonClient;
	}

	public List<ProcessInstanceDto> findProcessInstances(final String errandId, final String processKey, final String tenantId) {
		return operatonClient.findProcessInstances(errandId, processKey, tenantId);
	}

	public String startProcess(final String municipalityId, final String namespace, final String errandId, final String processKey, final String tenantId) {
		final var processInstance = operatonClient.startProcessWithTenant(processKey, tenantId, OperatonMapper.toStartProcessInstanceDto(municipalityId, namespace, errandId));
		return processInstance.getId();
	}

	public void correlateMessage(final String messageName, final String errandId, final String tenantId) {
		operatonClient.correlateMessage(OperatonMapper.toCorrelationMessageDto(messageName, errandId, tenantId));
	}

	public void deleteProcessInstance(final String processInstanceId) {
		operatonClient.deleteProcessInstance(processInstanceId, false);
	}
}
