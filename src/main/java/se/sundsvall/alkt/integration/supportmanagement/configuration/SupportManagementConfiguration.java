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

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	private static final String SENT_BY = PROCESS_SERVICE + "; type=processEngine";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(ClientRegistrationRepository clientRepository, SupportManagementProperties properties) {
		return FeignMultiCustomizer.create()
			// 404 keeps its status: an errand that is gone is an answer, not a gateway fault
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, List.of(NOT_FOUND.value())))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			.withRequestInterceptor(template -> template.header("X-Request-Group-Id", RequestId.get()))
			.withRequestInterceptor(template -> template.header(Identifier.HEADER_NAME, SENT_BY))
			.composeCustomizersToOne();
	}
}
