package se.sundsvall.alkt.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * What the case worker reads in the activity log. Steps and phases are keyed by their id in the model, and a key
 * holding an underscore must be written in brackets, or Spring drops the underscore.
 */
@Validated
@ConfigurationProperties("process-log")
public record ProcessLogProperties(
	@NotEmpty Map<String, @Valid @NotNull StepTexts> steps,
	@NotEmpty Map<String, @Valid @NotNull PhaseTexts> phases,
	@Valid @NotNull ProcessTexts process) {

	/**
	 * skipped only for a step the model lets the process go on without, rejected only for a step that can refuse its work.
	 */
	public record StepTexts(
		@NotBlank String done,
		@NotBlank String retry,
		@NotBlank String failed,
		String skipped,
		String rejected) {
	}

	public record PhaseTexts(@NotBlank String entered) {
	}

	/** What the reconciliation settles for a process that ended without saying so. */
	public record ProcessTexts(
		@NotBlank String settledCompleted,
		@NotBlank String settledTerminated) {
	}
}
