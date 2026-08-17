package se.sundsvall.alkt.integration.operaton.configuration;

import org.camunda.bpm.client.backoff.BackoffStrategy;
import org.camunda.bpm.client.backoff.ExponentialBackoffStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class BackoffConfigurationTest {

	@Autowired
	private BackoffStrategy backoffStrategy;

	@Test
	void testConfiguration() {
		assertThat(backoffStrategy).isInstanceOf(ExponentialBackoffStrategy.class);
		assertThat(backoffStrategy).hasFieldOrPropertyWithValue("initTime", 500L);
		assertThat(backoffStrategy).hasFieldOrPropertyWithValue("factor", 2F);
		assertThat(backoffStrategy).hasFieldOrPropertyWithValue("maxTime", 15000L);
	}
}
