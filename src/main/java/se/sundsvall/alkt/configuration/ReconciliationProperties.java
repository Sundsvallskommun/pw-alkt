package se.sundsvall.alkt.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** lookback: how far back in the history of the engine the reconciliation looks for instances that ended. */
@Validated
@ConfigurationProperties("reconciliation")
public record ReconciliationProperties(@NotNull Duration lookback) {
}
