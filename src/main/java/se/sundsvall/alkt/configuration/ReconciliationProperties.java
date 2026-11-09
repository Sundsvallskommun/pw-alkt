package se.sundsvall.alkt.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * lookback: how far back in the history of the engine the reconciliation looks for instances that ended, and how long
 * it follows up an incident after it was raised.
 */
@Validated
@ConfigurationProperties("reconciliation")
public record ReconciliationProperties(@NotNull Duration lookback) {
}
