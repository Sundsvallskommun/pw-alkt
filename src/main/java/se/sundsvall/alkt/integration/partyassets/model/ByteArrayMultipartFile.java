package se.sundsvall.alkt.integration.partyassets.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

import static java.util.Optional.ofNullable;

public class ByteArrayMultipartFile implements MultipartFile {

	private final String name;
	private final String originalFilename;
	private final String contentType;
	private final byte[] content;

	public ByteArrayMultipartFile(final String name, final String originalFilename, final String contentType, final byte[] content) {
		this.name = name;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.content = ofNullable(content).orElse(new byte[0]);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getOriginalFilename() {
		return originalFilename;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return content.length == 0;
	}

	@Override
	public long getSize() {
		return content.length;
	}

	@Override
	public byte[] getBytes() {
		return content.clone();
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(content);
	}

	@Override
	public void transferTo(final Path dest) throws IOException {
		Files.write(dest, content);
	}

	@Override
	public void transferTo(final File dest) throws IOException {
		transferTo(dest.toPath());
	}
}
