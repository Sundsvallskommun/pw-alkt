package se.sundsvall.alkt.integration.operaton.deployment;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** Only the folders in application.yaml are deployed, so a model anywhere else would never reach the engine. */
class ProcessModelFoldersTest {

	private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	@Test
	void everyModelLiesInADeployedFolder() throws IOException {
		final var folders = Arrays.stream(resolver.getResources("classpath*:processmodels/**/*.bpmn"))
			.map(ProcessModelFoldersTest::folderOf)
			.toList();

		assertThat(folders)
			.isNotEmpty()
			.allMatch(folder -> folder.matches("processmodels/(application|notification|inspection|reconciliation)"));
	}

	private static String folderOf(final Resource model) {
		try {
			final var path = model.getURL().getPath();
			final var folder = path.substring(0, path.lastIndexOf('/'));
			return folder.substring(folder.lastIndexOf("processmodels/"));
		} catch (final IOException | StringIndexOutOfBoundsException e) {
			return "unreadable: " + model;
		}
	}
}
