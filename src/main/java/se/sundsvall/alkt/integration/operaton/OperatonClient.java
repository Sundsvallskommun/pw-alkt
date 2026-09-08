package se.sundsvall.alkt.integration.operaton;

import feign.form.FormData;
import generated.se.sundsvall.operaton.ActivityInstanceDto;
import generated.se.sundsvall.operaton.CorrelationMessageDto;
import generated.se.sundsvall.operaton.DeploymentDto;
import generated.se.sundsvall.operaton.DeploymentWithDefinitionsDto;
import generated.se.sundsvall.operaton.EventSubscriptionDto;
import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import generated.se.sundsvall.operaton.HistoricProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
import generated.se.sundsvall.operaton.ProcessInstanceWithVariablesDto;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	url = "${integration.operaton.url}",
	configuration = OperatonConfiguration.class,
	dismiss404 = true)
@CircuitBreaker(name = CLIENT_ID)
public interface OperatonClient {

	@PostMapping(path = "process-definition/key/{key}/tenant-id/{tenantId}/start", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
	ProcessInstanceWithVariablesDto startProcessWithTenant(@PathVariable String key, @PathVariable String tenantId, StartProcessInstanceDto startProcessInstanceDto);

	@PostMapping(path = "message", consumes = APPLICATION_JSON_VALUE)
	void correlateMessage(CorrelationMessageDto correlationMessageDto);

	@PostMapping(path = "deployment/create", produces = APPLICATION_JSON_VALUE, consumes = MULTIPART_FORM_DATA_VALUE)
	DeploymentWithDefinitionsDto deploy(
		@PathVariable("tenant-id") String tenantId,
		@PathVariable("deployment-source") String deploymentSource,
		@PathVariable("deploy-changed-only") Boolean deployChangedOnly,
		@PathVariable("enable-duplicate-filtering") Boolean enableDuplicateFiltering,
		@PathVariable("deployment-name") String deploymentName,
		@PathVariable("deployment-activation-time") OffsetDateTime deploymentActivationTime,
		@PathVariable("data") FormData data);

	@GetMapping(path = "deployment", produces = APPLICATION_JSON_VALUE)
	List<DeploymentDto> getDeployments(@RequestParam("source") String source, @RequestParam("nameLike") String nameLike, @RequestParam("tenantIdIn") String tenantIdIn);

	@GetMapping(path = "process-instance/{id}", produces = APPLICATION_JSON_VALUE)
	Optional<ProcessInstanceDto> getProcessInstance(@PathVariable String id);

	@GetMapping(path = "process-instance", produces = APPLICATION_JSON_VALUE)
	List<ProcessInstanceDto> findProcessInstances(
		@RequestParam("businessKey") String businessKey,
		@RequestParam("processDefinitionKey") String processDefinitionKey,
		@RequestParam("tenantIdIn") String tenantIdIn);

	@GetMapping(path = "process-instance/{id}/activity-instances", produces = APPLICATION_JSON_VALUE)
	ActivityInstanceDto getProcessActivityInstance(@PathVariable String id);

	@DeleteMapping(path = "process-instance/{id}")
	void deleteProcessInstance(@PathVariable String id, @RequestParam("failIfNotExists") boolean failIfNotExists);

	@GetMapping(path = "history/process-instance/{id}", produces = APPLICATION_JSON_VALUE)
	HistoricProcessInstanceDto getHistoricProcessInstance(@PathVariable String id);

	@GetMapping(path = "history/activity-instance", produces = APPLICATION_JSON_VALUE)
	List<HistoricActivityInstanceDto> getHistoricActivities(@RequestParam("processInstanceId") String processInstanceId);

	@GetMapping(path = "event-subscription", produces = APPLICATION_JSON_VALUE)
	List<EventSubscriptionDto> getEventSubscriptions(@RequestParam("processInstanceId") String processInstanceId, @RequestParam("eventType") String eventType);
}
