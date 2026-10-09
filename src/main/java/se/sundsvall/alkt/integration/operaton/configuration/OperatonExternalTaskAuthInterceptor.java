package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.interceptor.ClientRequestContext;
import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import static java.util.Objects.isNull;
import static se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration.CLIENT_ID;

/**
 * Adds a WSO2 client-credentials bearer token to every external task request (fetchAndLock/complete/handleFailure) so
 * the service can poll api-service-operaton, which sits behind the OAuth2-secured gateway.
 * <p>
 * WSO2 can reject a token before it expires, and an interceptor never sees the response to drop it after a 401. So
 * every request gets a newly issued token, as in the other pw services.
 */
class OperatonExternalTaskAuthInterceptor implements ClientRequestInterceptor {

	static final String PRINCIPAL = "operaton-external-task-client";

	private final OAuth2AuthorizedClientManager authorizedClientManager;
	private final OAuth2AuthorizedClientService authorizedClientService;

	OperatonExternalTaskAuthInterceptor(final OAuth2AuthorizedClientManager authorizedClientManager, final OAuth2AuthorizedClientService authorizedClientService) {
		this.authorizedClientManager = authorizedClientManager;
		this.authorizedClientService = authorizedClientService;
	}

	@Override
	public void intercept(final ClientRequestContext requestContext) {
		authorizedClientService.removeAuthorizedClient(CLIENT_ID, PRINCIPAL);
		final var authorizedClient = authorizedClientManager.authorize(
			OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_ID).principal(PRINCIPAL).build());

		if (isNull(authorizedClient)) {
			throw new IllegalStateException("Could not obtain a WSO2 access token for client registration '" + CLIENT_ID + "'; check the OAuth2 client-credentials configuration");
		}

		requestContext.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authorizedClient.getAccessToken().getTokenValue());
	}
}
