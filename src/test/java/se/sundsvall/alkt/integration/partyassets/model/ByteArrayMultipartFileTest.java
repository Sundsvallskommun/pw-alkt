package se.sundsvall.alkt.integration.partyassets.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ByteArrayMultipartFileTest {

	private static final byte[] CONTENT = "content".getBytes();

	@TempDir
	private Path tempDir;

	@Test
	void describesTheFile() throws IOException {
		final var file = new ByteArrayMultipartFile("attachment", "beslut.pdf", "application/pdf", CONTENT);

		assertThat(file.getName()).isEqualTo("attachment");
		assertThat(file.getOriginalFilename()).isEqualTo("beslut.pdf");
		assertThat(file.getContentType()).isEqualTo("application/pdf");
		assertThat(file.isEmpty()).isFalse();
		assertThat(file.getSize()).isEqualTo(CONTENT.length);
		assertThat(file.getBytes()).isEqualTo(CONTENT);
		assertThat(file.getInputStream().readAllBytes()).isEqualTo(CONTENT);
	}

	@Test
	void treatsNoContentAsEmpty() {
		final var file = new ByteArrayMultipartFile("attachment", "empty.pdf", "application/pdf", null);

		assertThat(file.isEmpty()).isTrue();
		assertThat(file.getSize()).isZero();
	}

	@Test
	void transfersTheContent() throws IOException {
		final var file = new ByteArrayMultipartFile("attachment", "beslut.pdf", "application/pdf", CONTENT);
		final var toPath = tempDir.resolve("path.pdf");
		final var toFile = tempDir.resolve("file.pdf");

		file.transferTo(toPath);
		file.transferTo(toFile.toFile());

		assertThat(Files.readAllBytes(toPath)).isEqualTo(CONTENT);
		assertThat(Files.readAllBytes(toFile)).isEqualTo(CONTENT);
	}
}
