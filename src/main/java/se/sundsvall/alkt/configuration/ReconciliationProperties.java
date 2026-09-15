package se.sundsvall.alkt.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** lookback: how far back in the history of the engine the reconciliation looks for instances that ended. */
@ConfigurationProperties("reconciliation")
public record ReconciliationProperties(Duration lookback) {
}
