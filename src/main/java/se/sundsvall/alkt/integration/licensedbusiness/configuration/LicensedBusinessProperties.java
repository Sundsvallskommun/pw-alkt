package se.sundsvall.alkt.integration.licensedbusiness.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.licensed-business")
public record LicensedBusinessProperties(int connectTimeout, int readTimeout) {
}
