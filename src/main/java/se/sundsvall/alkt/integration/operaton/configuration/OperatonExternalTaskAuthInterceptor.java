package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.interceptor.ClientRequestContext;
import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import static java.util.Objects.isNull;
import static se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration.CLIENT_ID;

/**
 * Adds a WSO2 client-credentials bearer token to every external task request (fetchAndLock/complete/handleFailure) so
 * the service can poll api-service-operaton, which sits behind the OAuth2-secured gateway.
 * <p>
 * The token is cached by the authorized client manager and renewed once it is within the configured clock skew of
 * expiry (see {@link OperatonExternalTaskClientConfiguration}). It is deliberately <b>not</b> evicted per request: the
 * external task client polls continuously, and when tasks are available the backoff is reset to zero, so a forced
 * re-issue would mean one WSO2 token round trip per poll iteration.
 */
class OperatonExternalTaskAuthInterceptor implements ClientRequestInterceptor {

	static final String PRINCIPAL = "operaton-external-task-client";

	private final OAuth2AuthorizedClientManager authorizedClientManager;

	OperatonExternalTaskAuthInterceptor(final OAuth2AuthorizedClientManager authorizedClientManager) {
		this.authorizedClientManager = authorizedClientManager;
	}

	@Override
	public void intercept(final ClientRequestContext requestContext) {
		final var authorizedClient = authorizedClientManager.authorize(
			OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_ID).principal(PRINCIPAL).build());

		if (isNull(authorizedClient)) {
			throw new IllegalStateException("Could not obtain a WSO2 access token for client registration '" + CLIENT_ID + "'; check the OAuth2 client-credentials configuration");
		}

		requestContext.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authorizedClient.getAccessToken().getTokenValue());
	}
}
