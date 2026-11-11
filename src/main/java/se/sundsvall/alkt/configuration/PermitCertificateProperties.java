package se.sundsvall.alkt.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The letter that carries the permit certificate to the permit holder. letters is keyed by process key. */
@Validated
@ConfigurationProperties("permit-certificate")
public record PermitCertificateProperties(
	@NotBlank String organizationNumber,
	@NotBlank String department,
	@Valid @NotNull SupportInfo supportInfo,
	@NotEmpty Map<String, @Valid @NotNull Letter> letters) {

	public record SupportInfo(@NotBlank String text, String emailAddress, String phoneNumber, String url) {
	}

	public record Letter(@NotBlank String subject, @NotBlank String body) {
	}
}
