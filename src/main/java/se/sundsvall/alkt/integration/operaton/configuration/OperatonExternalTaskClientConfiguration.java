package se.sundsvall.alkt.integration.operaton.configuration;

import java.time.Duration;
import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Wires WSO2 authentication onto the external task client. The camunda-bpm external task client auto-registers every
 * {@link ClientRequestInterceptor} bean in the context. The token is obtained for the same {@code operaton} client
 * registration that the Feign {@code OperatonClient} uses.
 */
@Configuration
public class OperatonExternalTaskClientConfiguration {

	/**
	 * How long before its nominal expiry a cached token is considered expired and renewed. A generous skew absorbs clock
	 * drift between this service and WSO2 without forcing a token request per poll.
	 */
	private static final Duration TOKEN_CLOCK_SKEW = Duration.ofMinutes(2);

	@Bean
	ClientRequestInterceptor operatonExternalTaskAuthInterceptor(final ClientRegistrationRepository clientRegistrationRepository, final OAuth2AuthorizedClientService authorizedClientService) {
		final var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
		authorizedClientManager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials(clientCredentials -> clientCredentials.clockSkew(TOKEN_CLOCK_SKEW))
			.build());

		return new OperatonExternalTaskAuthInterceptor(authorizedClientManager);
	}
}
