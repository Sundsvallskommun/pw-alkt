package se.sundsvall.alkt.configuration;

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
import se.sundsvall.alkt.configuration.PermitJsonProperties.Settings;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class PermitJsonPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
		.withUserConfiguration(PropertiesConfiguration.class);

	@Autowired
	private PermitJsonProperties properties;

	@Test
	void eachSchemaHasItsVersionAndTheKeyOfItsDecision() {
		assertThat(properties.of(ALCOHOL)).isEqualTo(new Settings("0.1", "aot_alcohol_decision"));
		assertThat(properties.of(TOBACCO)).isEqualTo(new Settings("0.1", "aot_tobacco_decision"));
	}

	@Test
	void refusesToStartWithoutTheTobaccoSchema() {
		contextRunner.withPropertyValues("permit-json.alcohol.schema-version=1.0", "permit-json.alcohol.decision-key=aot_alcohol_decision")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("field 'tobacco'"));
	}

	@Test
	void refusesToStartWithoutTheVersionOfTheAlcoholSchema() {
		contextRunner.withPropertyValues("permit-json.alcohol.decision-key=aot_alcohol_decision",
			"permit-json.tobacco.schema-version=1.0", "permit-json.tobacco.decision-key=aot_tobacco_decision")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("field 'alcohol.schemaVersion'"));
	}

	@Configuration
	@EnableConfigurationProperties(PermitJsonProperties.class)
	static class PropertiesConfiguration {
	}
}
