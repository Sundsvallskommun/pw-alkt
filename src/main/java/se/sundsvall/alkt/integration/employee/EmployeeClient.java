package se.sundsvall.alkt.integration.employee;

import generated.se.sundsvall.employee.PortalPersonData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.sundsvall.alkt.integration.employee.configuration.EmployeeConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.employee.configuration.EmployeeConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	url = "${integration.employee.url}",
	configuration = EmployeeConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface EmployeeClient {

	@GetMapping(path = "/{municipalityId}/portalpersondata/{domain}/{loginName}", produces = APPLICATION_JSON_VALUE)
	PortalPersonData getPortalPersonData(
		@PathVariable String municipalityId,
		@PathVariable String domain,
		@PathVariable String loginName);
}
