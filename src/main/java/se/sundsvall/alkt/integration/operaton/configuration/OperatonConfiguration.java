package se.sundsvall.alkt.integration.operaton.configuration;

import java.util.List;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.JsonPathErrorDecoder;
import se.sundsvall.dept44.configuration.feign.decoder.JsonPathErrorDecoder.JsonPathSetup;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Import(FeignConfiguration.class)
public class OperatonConfiguration {

	public static final String CLIENT_ID = "operaton";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final OperatonProperties properties) {
		return FeignMultiCustomizer.create()
			// 400 keeps its status: a message that matches no wait state is an answer, and the only 400 the service expects
			.withErrorDecoder(new JsonPathErrorDecoder(CLIENT_ID, List.of(BAD_REQUEST.value()), new JsonPathSetup("$.type", "$.message")))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			.composeCustomizersToOne();
	}
}
