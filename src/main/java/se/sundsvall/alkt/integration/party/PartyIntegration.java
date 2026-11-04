package se.sundsvall.alkt.integration.party;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;

@Component
public class PartyIntegration {

	private final PartyClient partyClient;

	PartyIntegration(final PartyClient partyClient) {
		this.partyClient = partyClient;
	}

	public String getLegalId(final String municipalityId, final String partyId) {
		return partyClient.getLegalId(municipalityId, partyId)
			.orElseThrow(() -> new NonRetryableException("Party '%s' has no legal id".formatted(partyId)));
	}

	public Map<String, String> findLegalIds(final String municipalityId, final List<String> partyIds) {
		final var legalIds = new HashMap<String, String>();
		partyIds.stream()
			.filter(Objects::nonNull)
			.distinct()
			.forEach(partyId -> partyClient.getLegalId(municipalityId, partyId).ifPresent(legalId -> legalIds.put(partyId, legalId)));
		return legalIds;
	}
}
