package se.sundsvall.alkt.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import se.sundsvall.alkt.service.model.PermitSchema;

/** The JSON parameter of a permit, per schema of its area. */
@Validated
@ConfigurationProperties("permit-json")
public record PermitJsonProperties(@NotNull @Valid Settings alcohol, @NotNull @Valid Settings tobacco) {

	/**
	 * schemaVersion: the version of the schema party-assets writes the permit by. decisionKey: the key of the JSON
	 * parameter of the decision in Support Management.
	 */
	public record Settings(@NotBlank String schemaVersion, @NotBlank String decisionKey) {
	}

	public Settings of(final PermitSchema schema) {
		return switch (schema) {
			case ALCOHOL -> alcohol;
			case TOBACCO -> tobacco;
		};
	}
}
