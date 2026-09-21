package se.sundsvall.alkt.integration.supportmanagement.configuration;

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

import static feign.Request.HttpMethod.PUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;

@ExtendWith(MockitoExtension.class)
class SupportManagementConfigurationTest {

	@Mock
	private ClientRegistrationRepository clientRepositoryMock;

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Mock
	private SupportManagementProperties propertiesMock;

	@Spy
	private FeignMultiCustomizer feignMultiCustomizerSpy;

	@Captor
	private ArgumentCaptor<ErrorDecoder> errorDecoderCaptor;

	@Captor
	private ArgumentCaptor<RequestInterceptor> requestInterceptorCaptor;

	@InjectMocks
	private SupportManagementConfiguration configuration;

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
		verify(feignMultiCustomizerSpy, times(2)).withRequestInterceptor(requestInterceptorCaptor.capture());
		verify(feignMultiCustomizerSpy).composeCustomizersToOne();

		// Assert ErrorDecoder
		assertThat(errorDecoderCaptor.getValue())
			.isInstanceOf(ProblemErrorDecoder.class)
			.hasFieldOrPropertyWithValue("integrationName", CLIENT_ID);

		// Assert RequestInterceptors
		RequestId.init("test-request-id");
		try {
			final var requestTemplate = new RequestTemplate();
			requestInterceptorCaptor.getAllValues().forEach(interceptor -> interceptor.apply(requestTemplate));

			assertThat(requestTemplate.headers().get("X-Request-Group-Id")).containsExactly("test-request-id");
			assertThat(requestTemplate.headers().get("X-Sent-By")).containsExactly("pw-alkt; type=processEngine");
			assertThat(requestTemplate.headers()).doesNotContainKey("X-Trigger-Process");
		} finally {
			RequestId.reset();
		}
	}

	/**
	 * A 412 means the errand moved under a work step, and AbstractTaskWorker.isPreconditionFailed reads the status out
	 * of the message text because the decoder collapses it into BAD_GATEWAY. Pinned here so a dept44 upgrade that
	 * changes the message shows up as a failing test rather than as a step that silently stops rerunning. The body
	 * deliberately carries no status of its own, since the one in the message has to come from the response.
	 */
	@Test
	void decodesAPreconditionFailedSoTheStatusSurvivesInTheMessage() {

		final var response = errorResponse(412, """
			{
				"title": "Precondition Failed",
				"detail": "The errand has been updated by someone else"
			}
			""");

		assertThat(configuredErrorDecoder().decode("test", response))
			.isInstanceOf(ClientProblem.class)
			.hasFieldOrPropertyWithValue("status", BAD_GATEWAY)
			.hasMessageContaining("412");
	}

	/** An errand that is gone and a report refused for good are answers, not gateway faults, so they keep their status. */
	@Test
	void keepsTheStatusOfTheAnswersThatAreNotGatewayFaults() {

		final var gone = errorResponse(404, """
			{
				"title": "Not Found",
				"status": 404
			}
			""");
		final var refused = errorResponse(409, """
			{
				"title": "Conflict",
				"status": 409,
				"detail": "The process life of the errand is over"
			}
			""");

		final var errorDecoder = configuredErrorDecoder();

		assertThat(errorDecoder.decode("test", gone))
			.isInstanceOf(ClientProblem.class)
			.hasFieldOrPropertyWithValue("status", NOT_FOUND);
		assertThat(errorDecoder.decode("test", refused))
			.isInstanceOf(ClientProblem.class)
			.hasFieldOrPropertyWithValue("status", CONFLICT);
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
			.request(Request.create(PUT, "/2281/ALKT/errands/errandId/processes/processInstanceId", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(status)
			.build();
	}
}
