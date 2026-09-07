package se.sundsvall.alkt.integration.operaton.configuration;

import feign.Request;
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
import se.sundsvall.dept44.configuration.feign.decoder.JsonPathErrorDecoder;
import se.sundsvall.dept44.configuration.feign.decoder.JsonPathErrorDecoder.JsonPathSetup;
import se.sundsvall.dept44.exception.ClientProblem;

import static feign.Request.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.integration.operaton.configuration.OperatonConfiguration.CLIENT_ID;

@ExtendWith(MockitoExtension.class)
class OperatonConfigurationTest {

	@Mock
	private ClientRegistrationRepository clientRepositoryMock;

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Mock
	private OperatonProperties propertiesMock;

	@Spy
	private FeignMultiCustomizer feignMultiCustomizerSpy;

	@Captor
	private ArgumentCaptor<ErrorDecoder> errorDecoderCaptor;

	@InjectMocks
	private OperatonConfiguration configuration;

	@Test
	void testFeignBuilderCustomizer() {

		final var connectTimeout = 123;
		final var readTimeout = 321;

		when(propertiesMock.connectTimeout()).thenReturn(connectTimeout);
		when(propertiesMock.readTimeout()).thenReturn(readTimeout);
		when(clientRepositoryMock.findByRegistrationId(CLIENT_ID)).thenReturn(clientRegistrationMock);

		// Mock static FeignMultiCustomizer to enable spy and to verify that static method is being called
		try (MockedStatic<FeignMultiCustomizer> feignMultiCustomizerMock = Mockito.mockStatic(FeignMultiCustomizer.class)) {
			feignMultiCustomizerMock.when(FeignMultiCustomizer::create).thenReturn(feignMultiCustomizerSpy);

			configuration.feignBuilderCustomizer(clientRepositoryMock, propertiesMock);

			feignMultiCustomizerMock.verify(FeignMultiCustomizer::create);
		}

		// Verifications
		verify(propertiesMock).connectTimeout();
		verify(propertiesMock).readTimeout();
		verify(clientRepositoryMock).findByRegistrationId(CLIENT_ID);
		verify(feignMultiCustomizerSpy).withErrorDecoder(errorDecoderCaptor.capture());
		verify(feignMultiCustomizerSpy).withRequestTimeoutsInSeconds(connectTimeout, readTimeout);
		verify(feignMultiCustomizerSpy).withRetryableOAuth2InterceptorForClientRegistration(clientRegistrationMock);
		verify(feignMultiCustomizerSpy).composeCustomizersToOne();

		// Assert ErrorDecoder
		assertThat(errorDecoderCaptor.getValue())
			.isInstanceOf(JsonPathErrorDecoder.class)
			.hasFieldOrPropertyWithValue("integrationName", CLIENT_ID)
			.hasFieldOrPropertyWithValue("jsonPathSetup", new JsonPathSetup("$.type", "$.message"));
	}

	@Test
	void decodesOperatonExceptionDto() {

		final var response = errorResponse(400, """
			{
				"type": "MismatchingMessageCorrelationException",
				"message": "Cannot correlate message 'errandUpdated': No process definition or execution matches the parameters"
			}
			""");

		final var exception = configuredErrorDecoder().decode("test", response);

		assertThat(exception)
			.isInstanceOf(ClientProblem.class)
			.hasMessageContaining("title=MismatchingMessageCorrelationException")
			.hasMessageContaining("detail=Cannot correlate message 'errandUpdated': No process definition or execution matches the parameters");
	}

	/**
	 * Operaton answers POST /message with 400 both when the message matches no execution and when it matches several, and
	 * both carry the same type. Only the message text tells them apart, which is why it has to survive decoding.
	 */
	@Test
	void decodesTwoCorrelationFailuresWithTheSameTypeDifferently() {

		final var noMatch = errorResponse(400, """
			{
				"type": "MismatchingMessageCorrelationException",
				"message": "Cannot correlate message 'errandUpdated': No process definition or execution matches the parameters"
			}
			""");
		final var severalMatches = errorResponse(400, """
			{
				"type": "MismatchingMessageCorrelationException",
				"message": "Cannot correlate message 'errandUpdated': 2 executions match the correlation keys"
			}
			""");

		final var errorDecoder = configuredErrorDecoder();

		assertThat(errorDecoder.decode("test", severalMatches))
			.hasMessageContaining("detail=Cannot correlate message 'errandUpdated': 2 executions match the correlation keys")
			.hasMessageNotContaining("No process definition or execution matches");
		assertThat(errorDecoder.decode("test", noMatch))
			.hasMessageNotContaining("2 executions match");
	}

	/**
	 * Known limitation of reading the body by JSON path: a definite path that is not present makes Jayway throw, and the
	 * whole extraction falls back to a title of "Unknown error". Pinned here so a dept44 upgrade that changes it shows up.
	 */
	@Test
	void fallsBackWhenBodyCarriesNoMessage() {

		final var response = errorResponse(400, """
			{
				"type": "MismatchingMessageCorrelationException"
			}
			""");

		assertThat(configuredErrorDecoder().decode("test", response))
			.isInstanceOf(ClientProblem.class)
			.hasMessageContaining("title=Unknown error")
			.hasMessageNotContaining("MismatchingMessageCorrelationException");
	}

	/**
	 * Returns the decoder that the configuration actually wires in, so the decoding tests run against the real setup
	 * rather than a hand-built copy of it.
	 */
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
			.request(Request.create(POST, "/message", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(status)
			.build();
	}
}
