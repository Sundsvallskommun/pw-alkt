package se.sundsvall.alkt.integration.operaton.configuration;

import java.time.Instant;
import org.camunda.bpm.client.interceptor.ClientRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration.CLIENT_ID;

@ExtendWith(MockitoExtension.class)
class OperatonExternalTaskAuthInterceptorTest {

	@Mock
	private OAuth2AuthorizedClientManager authorizedClientManagerMock;

	@Mock
	private ClientRequestContext requestContextMock;

	@Captor
	private ArgumentCaptor<OAuth2AuthorizeRequest> authorizeRequestCaptor;

	private static OAuth2AuthorizedClient authorizedClientWithToken(final String tokenValue) {
		final var issuedAt = Instant.parse("2026-01-01T00:00:00Z");
		final var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue, issuedAt, issuedAt.plusSeconds(60));
		final var authorizedClient = mock(OAuth2AuthorizedClient.class);
		when(authorizedClient.getAccessToken()).thenReturn(accessToken);
		return authorizedClient;
	}

	@Test
	void addsBearerTokenHeader() {
		// The authorized client has to be stubbed before the outer when(), or Mockito sees a nested stubbing
		final var authorizedClient = authorizedClientWithToken("the-token");
		when(authorizedClientManagerMock.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient);

		new OperatonExternalTaskAuthInterceptor(authorizedClientManagerMock).intercept(requestContextMock);

		verify(requestContextMock).addHeader("Authorization", "Bearer the-token");
		verify(authorizedClientManagerMock).authorize(authorizeRequestCaptor.capture());
		assertThat(authorizeRequestCaptor.getValue().getClientRegistrationId()).isEqualTo(CLIENT_ID);
		assertThat(authorizeRequestCaptor.getValue().getPrincipal().getName()).isEqualTo(OperatonExternalTaskAuthInterceptor.PRINCIPAL);
	}

	/**
	 * The cached token is reused across requests - the manager renews it on expiry. Evicting per request would mean one
	 * WSO2 token round trip per external task poll.
	 */
	@Test
	void reusesTheCachedTokenAcrossRequests() {
		final var authorizedClient = authorizedClientWithToken("the-token");
		when(authorizedClientManagerMock.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient);

		final var interceptor = new OperatonExternalTaskAuthInterceptor(authorizedClientManagerMock);
		interceptor.intercept(requestContextMock);
		interceptor.intercept(requestContextMock);

		// Two requests, two authorize calls - and no eviction in between, so the manager is free to hand back its cache
		verify(authorizedClientManagerMock, times(2)).authorize(any(OAuth2AuthorizeRequest.class));
		verify(requestContextMock, times(2)).addHeader("Authorization", "Bearer the-token");
	}

	@Test
	void throwsWhenNoTokenCanBeObtained() {
		when(authorizedClientManagerMock.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

		final var interceptor = new OperatonExternalTaskAuthInterceptor(authorizedClientManagerMock);

		assertThatThrownBy(() -> interceptor.intercept(requestContextMock))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(CLIENT_ID);

		verifyNoInteractions(requestContextMock);
	}
}
