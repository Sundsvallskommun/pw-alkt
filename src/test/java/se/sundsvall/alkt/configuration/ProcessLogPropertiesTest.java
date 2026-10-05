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
import se.sundsvall.alkt.configuration.ProcessLogProperties.PhaseTexts;
import se.sundsvall.alkt.configuration.ProcessLogProperties.ProcessTexts;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class ProcessLogPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
		.withUserConfiguration(PropertiesConfiguration.class)
		.withPropertyValues(
			"process-log.steps.[external_task_create_asset].done=Klart",
			"process-log.steps.[external_task_create_asset].retry=Nytt försök",
			"process-log.phases.[review_phase].entered=Granskning har påbörjats",
			"process-log.process.settled-completed=Avstämd som avslutad",
			"process-log.process.settled-terminated=Avstämd som avbruten");

	@Autowired
	private ProcessLogProperties properties;

	/** The keys hold underscores, which Spring keeps only for a key written in brackets. */
	@Test
	void testProperties() {
		assertThat(properties.steps()).containsOnlyKeys(
			"external_task_notify_processing_started",
			"external_task_check_decision",
			"external_task_create_decision",
			"external_task_create_asset",
			"external_task_create_change_draft",
			"external_task_update_asset",
			"external_task_complete_process",
			"external_task_cancel_process");
		assertThat(properties.steps().get("external_task_create_asset").done()).isEqualTo("Tillståndet har registrerats hos tillståndshavaren");
		assertThat(properties.steps().get("external_task_create_asset").skipped()).isNull();
		assertThat(properties.steps().get("external_task_notify_processing_started").skipped()).isNotBlank();
		assertThat(properties.phases()).containsOnlyKeys(
			"registration_phase",
			"review_phase",
			"investigation_phase",
			"decision_phase",
			"follow_up_phase",
			"closure_phase",
			"cancel_process_subprocess");
		assertThat(properties.phases().get("review_phase")).isEqualTo(new PhaseTexts("Granskning har påbörjats"));
		assertThat(properties.process()).isEqualTo(new ProcessTexts(
			"Processen avslutades utan att rapportera det och har stämts av i efterhand",
			"Processen avbröts utanför ärendet och har stämts av i efterhand"));
	}

	/** A text left out when the configuration is written by hand stops the start, rather than falling back unnoticed. */
	@Test
	void refusesAStepWithoutTheTextOfAnOutcome() {
		contextRunner.run(context -> assertThat(context).hasFailed()
			.getFailure().rootCause().hasMessageContaining("failed"));
	}

	@Test
	void acceptsAStepWithEveryTextButSkipped() {
		contextRunner.withPropertyValues("process-log.steps.[external_task_create_asset].failed=Processen har stannat")
			.run(context -> assertThat(context).hasNotFailed()
				.getBean(ProcessLogProperties.class).extracting(ProcessLogProperties::steps).satisfies(steps -> assertThat(steps).containsOnlyKeys("external_task_create_asset")));
	}

	@Configuration
	@EnableConfigurationProperties(ProcessLogProperties.class)
	static class PropertiesConfiguration {
	}
}
