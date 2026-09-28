package se.sundsvall.alkt.integration.templating;

import generated.se.sundsvall.templating.RenderRequest;
import generated.se.sundsvall.templating.RenderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import se.sundsvall.alkt.integration.templating.configuration.TemplatingConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.alkt.integration.templating.configuration.TemplatingConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.templating.url}", configuration = TemplatingConfiguration.class)
@CircuitBreaker(name = CLIENT_ID)
public interface TemplatingClient {

	@PostMapping(path = "/{municipalityId}/render", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ResponseEntity<RenderResponse> render(
		@PathVariable String municipalityId,
		@RequestBody RenderRequest request);
}
