package se.sundsvall.alkt.integration.partyassets.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("integration.party-assets")
public record PartyAssetsProperties(int connectTimeout, int readTimeout, @NotBlank String relationType) {
}
