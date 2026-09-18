package se.sundsvall.alkt.integration.party;

import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@Component
public class PartyIntegration {

	private final PartyClient partyClient;

	PartyIntegration(final PartyClient partyClient) {
		this.partyClient = partyClient;
	}

	public String getLegalId(final String municipalityId, final String partyId) {
		return partyClient.getLegalId(municipalityId, partyId)
			.orElseThrow(() -> Problem.valueOf(UNPROCESSABLE_CONTENT, "Party '%s' has no legal id".formatted(partyId)));
	}
}
