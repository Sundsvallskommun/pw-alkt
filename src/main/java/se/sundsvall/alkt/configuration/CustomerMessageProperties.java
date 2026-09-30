package se.sundsvall.alkt.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** texts: the messages to the customer, keyed by the name a step gives in its input parameter message. */
@Validated
@ConfigurationProperties("customer-message")
public record CustomerMessageProperties(@NotEmpty Map<String, @NotBlank String> texts) {
}
