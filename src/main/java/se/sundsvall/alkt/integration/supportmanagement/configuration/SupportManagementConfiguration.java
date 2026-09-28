package se.sundsvall.alkt.integration.supportmanagement.configuration;

import java.util.List;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static se.sundsvall.alkt.Constants.SENT_BY;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(ClientRegistrationRepository clientRepository, SupportManagementProperties properties) {
		return FeignMultiCustomizer.create()
			// 404, 409 and 412 keep their status: an errand that is gone, a report refused for good or an errand that moved under
			// the report are answers, not gateway faults
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, List.of(NOT_FOUND.value(), CONFLICT.value(), PRECONDITION_FAILED.value())))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			.withRequestInterceptor(template -> template.header("X-Request-Group-Id", RequestId.get()))
			.withRequestInterceptor(template -> template.header(Identifier.HEADER_NAME, SENT_BY))
			.composeCustomizersToOne();
	}
}
