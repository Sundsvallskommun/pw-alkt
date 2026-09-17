package se.sundsvall.alkt.integration.partyassets.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.party-assets")
public record PartyAssetsProperties(int connectTimeout, int readTimeout) {
}
