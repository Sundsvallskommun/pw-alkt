package se.sundsvall.alkt.integration.party;

import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;

@Component
public class PartyIntegration {

	private final PartyClient partyClient;

	PartyIntegration(final PartyClient partyClient) {
		this.partyClient = partyClient;
	}

	// Why: a party the register does not know stays unknown, so a retry would only delay the incident.
	public String getLegalId(final String municipalityId, final String partyId) {
		return partyClient.getLegalId(municipalityId, partyId)
			.orElseThrow(() -> new NonRetryableException("Party '%s' has no legal id".formatted(partyId)));
	}
}
