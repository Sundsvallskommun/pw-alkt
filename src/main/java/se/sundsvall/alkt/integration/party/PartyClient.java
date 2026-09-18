package se.sundsvall.alkt.integration.party;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.sundsvall.alkt.integration.party.configuration.PartyConfiguration;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static se.sundsvall.alkt.integration.party.configuration.PartyConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	url = "${integration.party.url}",
	configuration = PartyConfiguration.class,
	dismiss404 = true)
@CircuitBreaker(name = CLIENT_ID)
public interface PartyClient {

	/**
	 * The legal id behind a partyId, a personal identity number or an organization number. Empty for a party the
	 * register does not know, which is what dismiss404 turns the 404 into.
	 */
	@GetMapping(path = "/{municipalityId}/partyId/{partyId}/legalId", produces = TEXT_PLAIN_VALUE)
	Optional<String> getLegalId(
		@PathVariable String municipalityId,
		@PathVariable String partyId);
}
