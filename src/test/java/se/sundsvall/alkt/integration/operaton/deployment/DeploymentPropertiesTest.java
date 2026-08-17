package se.sundsvall.alkt.integration.operaton.deployment;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.operaton.deployment.DeploymentProperties.ProcessArchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@SpringBootTest(classes = Application.class, webEnvironment = MOCK)
@ActiveProfiles("junit")
class DeploymentPropertiesTest {

	@Autowired
	private DeploymentProperties deploymentProperties;

	@Test
	void testAnnotation() {
		assertThat(DeploymentProperties.class).hasAnnotation(ConfigurationProperties.class);
		assertThat(DeploymentProperties.class.getAnnotation(ConfigurationProperties.class).value()).isEqualTo("process-engine.deployment");
	}

	@Test
	void emptyDeploymentProperties() {
		final DeploymentProperties props = new DeploymentProperties();

		assertThat(props.getProcesses())
			.isNull();
	}

	@Test
	void verifyExternalConfiguration() {
		assertThat(deploymentProperties.isAutoDeployEnabled()).isFalse();
		assertThat(deploymentProperties.getProcesses()).hasSize(2)
			.extracting(
				ProcessArchive::name,
				ProcessArchive::tenant,
				ProcessArchive::bpmnResourcePattern,
				ProcessArchive::dmnResourcePattern,
				ProcessArchive::formResourcePattern)
			.containsExactlyInAnyOrder(
				tuple(
					"process_name_1",
					"tenant_id_1",
					"bpmnResourcePattern_1",
					"dmnResourcePattern_1",
					"formResourcePattern_1"),
				tuple(
					"process_name_2",
					"tenant_id_2",
					"bpmnResourcePattern_2",
					"dmnResourcePattern_2",
					"formResourcePattern_2"));
	}

	@Test
	void withDeploymentProperties() {
		final var autoDeploy = false;
		final var name = "name";
		final var tenant = "tenant";
		final var bpmnResources = "bpmnResources";
		final var dmnResources = "dmnResources";
		final var formResources = "formResources";
		final var processArchive = new ProcessArchive(name, tenant, bpmnResources, dmnResources, formResources);

		final DeploymentProperties props = new DeploymentProperties();
		props.setAutoDeployEnabled(autoDeploy);
		props.setProcesses(List.of(processArchive));

		assertThat(props.isAutoDeployEnabled()).isFalse();
		assertThat(props.getProcesses())
			.isNotNull()
			.containsExactly(processArchive);
	}

	@Test
	void emptyProcessArchive() {
		assertThat(new ProcessArchive(null, null, null, null, null)).hasAllNullFieldsOrProperties();
	}
}
