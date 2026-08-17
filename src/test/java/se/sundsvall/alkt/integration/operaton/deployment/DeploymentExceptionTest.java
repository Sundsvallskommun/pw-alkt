package se.sundsvall.alkt.integration.operaton.deployment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentExceptionTest {

	@Test
	void testInheritance() {
		assertThat(DeploymentException.class).hasSuperclass(RuntimeException.class);
	}
}
