package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.backoff.BackoffStrategy;
import org.camunda.bpm.client.backoff.ExponentialBackoffStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class BackoffConfiguration {

	@Bean
	@Primary
	public BackoffStrategy backoffStrategyConfiguration(BackoffProperties properties) {
		return new ExponentialBackoffStrategy(properties.initTime(), properties.factor(), properties.maxTime());
	}

}
