package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.backoff.BackoffStrategy;
import org.camunda.bpm.client.backoff.ExponentialErrorBackoffStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class BackoffConfiguration {

	// Why: error-aware, so an empty long poll is followed by the next one at once. A plain exponential backoff
	// waits after an empty poll as well, and a task created in that gap waits with it.
	@Bean
	@Primary
	public BackoffStrategy backoffStrategyConfiguration(BackoffProperties properties) {
		return new ExponentialErrorBackoffStrategy(properties.initTime(), properties.factor(), properties.maxTime());
	}

}
