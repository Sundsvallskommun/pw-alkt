package se.sundsvall.alkt.configuration;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class ReconciliationPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
		.withUserConfiguration(PropertiesConfiguration.class);

	@Autowired
	private ReconciliationProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.lookback()).isEqualTo(Duration.ofHours(2));
		assertThat(properties.nightlyLookback()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void bindsTheShortAndTheNightlyWindow() {
		contextRunner.withPropertyValues("reconciliation.lookback=PT30M", "reconciliation.nightly-lookback=P3D")
			.run(context -> assertThat(context).hasNotFailed()
				.getBean(ReconciliationProperties.class).isEqualTo(new ReconciliationProperties(Duration.ofMinutes(30), Duration.ofDays(3))));
	}

	@Test
	void refusesToStartWithoutTheNightlyWindow() {
		contextRunner.withPropertyValues("reconciliation.lookback=PT2H")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("nightlyLookback"));
	}

	@Test
	void refusesToStartWithoutTheShortWindow() {
		contextRunner.withPropertyValues("reconciliation.nightly-lookback=P7D")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("field 'lookback'"));
	}

	@Test
	void hasNoIncidentLookbackAnyMore() {
		assertThat(Arrays.stream(ReconciliationProperties.class.getRecordComponents()).map(component -> component.getName()))
			.containsExactly("lookback", "nightlyLookback");
	}

	@Configuration
	@EnableConfigurationProperties(ReconciliationProperties.class)
	static class PropertiesConfiguration {
	}
}
