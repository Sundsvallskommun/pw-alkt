package se.sundsvall.alkt.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** labelId: the id of the label the action errand gets, whose metadata in Support Management starts its process. */
@Validated
@ConfigurationProperties("action-errand")
public record ActionErrandProperties(@NotBlank String labelId) {
}
