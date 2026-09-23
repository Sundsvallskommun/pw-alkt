package se.sundsvall.alkt.integration.messaging.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.messaging")
public record MessagingProperties(int connectTimeout, int readTimeout, Slack slack) {

	public record Slack(String token, String channel) {
	}
}
