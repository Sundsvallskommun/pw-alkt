package se.sundsvall.alkt.service.model;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.service.model.ProcessStatus.RETRYING;

class ProcessStateReportTest {

	@Test
	void completed() {
		final var report = ProcessStateReport.completed();

		assertThat(report.status()).isEqualTo(COMPLETED);
		assertThat(report.currentActivityId()).isNull();
		assertThat(report.currentActivityName()).isNull();
		assertThat(report.error()).isNull();
		assertThat(report.activities()).isEmpty();
	}

	@Test
	void failed() {
		final var report = ProcessStateReport.failed("INCIDENT", "Timeout");

		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.error()).isNotNull();
		assertThat(report.error().getCode()).isEqualTo("INCIDENT");
		assertThat(report.error().getMessage()).isEqualTo("Timeout");
		assertThat(report.activities()).isEmpty();
	}

	@Test
	void retrying() {
		final var report = ProcessStateReport.retrying("RETRY", "Timeout");

		assertThat(report.status()).isEqualTo(RETRYING);
		assertThat(report.error()).isNotNull();
		assertThat(report.error().getCode()).isEqualTo("RETRY");
	}

	/** Support Management answers 400 on a longer value, and the failure handler would swallow that answer. */
	@Test
	void cutsTheErrorToWhatSupportManagementAccepts() {
		final var report = ProcessStateReport.failed("x".repeat(100), "y".repeat(3000));

		assertThat(report.error().getCode()).hasSize(64).endsWith("...");
		assertThat(report.error().getMessage()).hasSize(2048).endsWith("...");
	}

	@Test
	void nullActivitiesBecomeAnEmptyList() {
		assertThat(new ProcessStateReport(COMPLETED, null, null, null, null).activities()).isEmpty();
	}

	@Test
	void activitiesAreCopied() {
		final var activities = new ArrayList<>(List.of(new ProcessActivity().activityType("PHASE")));

		final var report = new ProcessStateReport(COMPLETED, null, null, null, activities);
		activities.clear();

		assertThat(report.activities()).hasSize(1);
	}

	@Test
	void atActivityKeepsEverythingElse() {
		final var report = ProcessStateReport.failed("INCIDENT", "Timeout").atActivity("investigation_phase");

		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.currentActivityId()).isEqualTo("investigation_phase");
		assertThat(report.error().getCode()).isEqualTo("INCIDENT");
		assertThat(report.activities()).isEmpty();
	}
}
