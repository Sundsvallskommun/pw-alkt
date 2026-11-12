package se.sundsvall.alkt.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * lookback: how far back the five-minute reconciliation looks for incidents and instances that ended.
 * nightlyLookback: how far back the nightly reconciliation looks for the same.
 */
@Validated
@ConfigurationProperties("reconciliation")
public record ReconciliationProperties(@NotNull Duration lookback, @NotNull Duration nightlyLookback) {
}
