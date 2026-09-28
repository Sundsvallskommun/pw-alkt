package se.sundsvall.alkt.integration.templating.configuration;

import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static feign.Request.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.alkt.integration.templating.configuration.TemplatingConfiguration.CLIENT_ID;

@ExtendWith(MockitoExtension.class)
class TemplatingConfigurationTest {

	@Mock
	private ClientRegistrationRepository clientRepositoryMock;

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Mock
	private TemplatingProperties propertiesMock;

	@Spy
	private FeignMultiCustomizer feignMultiCustomizerSpy;

	@Captor
	private ArgumentCaptor<ErrorDecoder> errorDecoderCaptor;

	@Captor
	private ArgumentCaptor<RequestInterceptor> requestInterceptorCaptor;

	@InjectMocks
	private TemplatingConfiguration configuration;

	@Test
	void testFeignBuilderCustomizer() {

		final var connectTimeout = 123;
		final var readTimeout = 321;

		when(propertiesMock.connectTimeout()).thenReturn(connectTimeout);
		when(propertiesMock.readTimeout()).thenReturn(readTimeout);
		when(clientRepositoryMock.findByRegistrationId(CLIENT_ID)).thenReturn(clientRegistrationMock);

		try (MockedStatic<FeignMultiCustomizer> feignMultiCustomizerMock = Mockito.mockStatic(FeignMultiCustomizer.class)) {
			feignMultiCustomizerMock.when(FeignMultiCustomizer::create).thenReturn(feignMultiCustomizerSpy);

			configuration.feignBuilderCustomizer(clientRepositoryMock, propertiesMock);

			feignMultiCustomizerMock.verify(FeignMultiCustomizer::create);
		}

		verify(propertiesMock).connectTimeout();
		verify(propertiesMock).readTimeout();
		verify(clientRepositoryMock).findByRegistrationId(CLIENT_ID);
		verify(feignMultiCustomizerSpy).withErrorDecoder(errorDecoderCaptor.capture());
		verify(feignMultiCustomizerSpy).withRequestTimeoutsInSeconds(connectTimeout, readTimeout);
		verify(feignMultiCustomizerSpy).withRetryableOAuth2InterceptorForClientRegistration(clientRegistrationMock);
		verify(feignMultiCustomizerSpy, times(2)).withRequestInterceptor(requestInterceptorCaptor.capture());
		verify(feignMultiCustomizerSpy).composeCustomizersToOne();

		assertThat(errorDecoderCaptor.getValue())
			.isInstanceOf(ProblemErrorDecoder.class)
			.hasFieldOrPropertyWithValue("integrationName", CLIENT_ID);

		RequestId.init("test-request-id");
		try {
			final var requestTemplate = new RequestTemplate();
			requestInterceptorCaptor.getAllValues().forEach(interceptor -> interceptor.apply(requestTemplate));

			assertThat(requestTemplate.headers().get("X-Request-Group-Id")).containsExactly("test-request-id");
			assertThat(requestTemplate.headers().get("X-Sent-By")).containsExactly("pw-alkt; type=processEngine");
		} finally {
			RequestId.reset();
		}
	}

	/** A strict template missing a parameter answers 400, and TemplatingIntegration fails without retry on that status. */
	@Test
	void decodesABadRequestWithItsStatus() {

		final var response = errorResponse(400, """
			{
				"title": "Bad Request",
				"status": 400,
				"detail": "Missing template parameter 'premisesName'"
			}
			""");

		assertThat(configuredErrorDecoder().decode("test", response))
			.isInstanceOf(ClientProblem.class)
			.hasFieldOrPropertyWithValue("status", BAD_REQUEST)
			.hasMessageContaining("premisesName");
	}

	@Test
	void decodesAnyOtherFailureAsAGatewayFault() {

		final var response = errorResponse(404, """
			{
				"title": "Not Found",
				"status": 404
			}
			""");

		assertThat(configuredErrorDecoder().decode("test", response))
			.isInstanceOf(ClientProblem.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY);
	}

	/** Returns the decoder the configuration actually wires in, so the decoding runs against the real setup. */
	private ErrorDecoder configuredErrorDecoder() {

		when(propertiesMock.connectTimeout()).thenReturn(1);
		when(propertiesMock.readTimeout()).thenReturn(1);
		when(clientRepositoryMock.findByRegistrationId(CLIENT_ID)).thenReturn(clientRegistrationMock);

		try (MockedStatic<FeignMultiCustomizer> feignMultiCustomizerMock = Mockito.mockStatic(FeignMultiCustomizer.class)) {
			feignMultiCustomizerMock.when(FeignMultiCustomizer::create).thenReturn(feignMultiCustomizerSpy);

			configuration.feignBuilderCustomizer(clientRepositoryMock, propertiesMock);
		}

		verify(feignMultiCustomizerSpy).withErrorDecoder(errorDecoderCaptor.capture());

		return errorDecoderCaptor.getValue();
	}

	private static Response errorResponse(final int status, final String body) {
		return Response.builder()
			.body(body, UTF_8)
			.request(Request.create(POST, "/2281/render/pdf", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(status)
			.build();
	}
}
