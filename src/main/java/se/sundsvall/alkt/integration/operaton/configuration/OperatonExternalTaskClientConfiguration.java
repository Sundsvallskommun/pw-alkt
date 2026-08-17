package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Wires WSO2 OAuth2 authentication onto the external task client. The camunda-bpm external task client auto-registers
 * every {@link ClientRequestInterceptor} bean in the context. The token is obtained for the same {@code operaton}
 * client registration that the Feign {@code OperatonClient} uses.
 */
@Configuration
public class OperatonExternalTaskClientConfiguration {

	@Bean
	ClientRequestInterceptor operatonExternalTaskAuthInterceptor(final ClientRegistrationRepository clientRegistrationRepository, final OAuth2AuthorizedClientService authorizedClientService) {
		final var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
		authorizedClientManager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

		return new OperatonExternalTaskAuthInterceptor(authorizedClientManager, authorizedClientService);
	}
}
