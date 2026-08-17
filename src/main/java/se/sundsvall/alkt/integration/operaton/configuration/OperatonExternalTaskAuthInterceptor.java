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
 */
class OperatonExternalTaskAuthInterceptor implements ClientRequestInterceptor {

	private static final String PRINCIPAL = "operaton-external-task-client";

	private final OAuth2AuthorizedClientManager authorizedClientManager;
	private final OAuth2AuthorizedClientService authorizedClientService;

	OperatonExternalTaskAuthInterceptor(final OAuth2AuthorizedClientManager authorizedClientManager, final OAuth2AuthorizedClientService authorizedClientService) {
		this.authorizedClientManager = authorizedClientManager;
		this.authorizedClientService = authorizedClientService;
	}

	@Override
	public void intercept(final ClientRequestContext requestContext) {
		// Evict any cached token before authorizing so every poll forces a freshly issued token. The manager only renews
		// near the nominal expiresAt, but WSO2 may invalidate a token server-side early (gateway restart, revocation,
		// clock drift); since this interceptor never sees the response it cannot detect a 401 and evict afterwards.
		authorizedClientService.removeAuthorizedClient(CLIENT_ID, PRINCIPAL);

		final var authorizedClient = authorizedClientManager.authorize(
			OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_ID).principal(PRINCIPAL).build());

		if (isNull(authorizedClient)) {
			throw new IllegalStateException("Could not obtain a WSO2 access token for client registration '" + CLIENT_ID + "'; check the OAuth2 client-credentials configuration");
		}

		requestContext.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authorizedClient.getAccessToken().getTokenValue());
	}
}
