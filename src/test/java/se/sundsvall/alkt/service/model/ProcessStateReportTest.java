package se.sundsvall.alkt.service.model;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.service.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.service.model.ProcessStatus.RETRYING;
import static se.sundsvall.alkt.service.model.ProcessStatus.RUNNING;
import static se.sundsvall.alkt.service.model.ProcessStatus.WAITING;

class ProcessStateReportTest {

	@Test
	void running() {
		final var report = ProcessStateReport.running("activityId", "activityName");

		assertThat(report.status()).isEqualTo(RUNNING);
		assertThat(report.currentActivityId()).isEqualTo("activityId");
		assertThat(report.currentActivityName()).isEqualTo("activityName");
		assertThat(report.errandVersion()).isNull();
		assertThat(report.error()).isNull();
		assertThat(report.activities()).isEmpty();
		assertThat(report.variables()).isEmpty();
	}

	@Test
	void waiting() {
		final var report = ProcessStateReport.waiting("activityId", "activityName");

		assertThat(report.status()).isEqualTo(WAITING);
		assertThat(report.currentActivityId()).isEqualTo("activityId");
		assertThat(report.currentActivityName()).isEqualTo("activityName");
		assertThat(report.error()).isNull();
		assertThat(report.awaitingSignals()).isEmpty();
	}

	@Test
	void completed() {
		final var report = ProcessStateReport.completed();

		assertThat(report.status()).isEqualTo(COMPLETED);
		assertThat(report.currentActivityId()).isNull();
		assertThat(report.currentActivityName()).isNull();
		assertThat(report.errandVersion()).isNull();
		assertThat(report.error()).isNull();
		assertThat(report.activities()).isEmpty();
		assertThat(report.variables()).isEmpty();
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
	void nullCollectionsBecomeEmpty() {
		final var report = new ProcessStateReport(COMPLETED, null, null, null, null, null, null, null);

		assertThat(report.activities()).isEmpty();
		assertThat(report.awaitingSignals()).isEmpty();
		assertThat(report.variables()).isEmpty();
	}

	@Test
	void activitiesAreCopied() {
		final var activities = new ArrayList<>(List.of(new ProcessActivity().activityType("PHASE")));

		final var report = new ProcessStateReport(COMPLETED, null, null, null, null, activities, null, null);
		activities.clear();

		assertThat(report.activities()).hasSize(1);
	}

	@Test
	void atActivityKeepsEverythingElse() {
		final var report = ProcessStateReport.failed("INCIDENT", "Timeout").withErrandVersion(7L).atActivity("investigation_phase");

		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.currentActivityId()).isEqualTo("investigation_phase");
		assertThat(report.errandVersion()).isEqualTo(7L);
		assertThat(report.error().getCode()).isEqualTo("INCIDENT");
		assertThat(report.activities()).isEmpty();
	}

	@Test
	void withErrandVersionReplacesOnlyTheVersion() {
		final var report = ProcessStateReport.completed().withErrandVersion(7L);

		assertThat(report.errandVersion()).isEqualTo(7L);
		assertThat(report.status()).isEqualTo(COMPLETED);
	}

	@Test
	void awaitingSignalsAreCopied() {
		final var signals = new ArrayList<>(List.of(new AwaitingSignal("review_completed", null)));

		final var report = ProcessStateReport.waiting("review_phase", "Review").withAwaitingSignals(signals);
		signals.clear();

		assertThat(report.awaitingSignals()).hasSize(1);
	}

	@Test
	void withActivitiesReplacesOnlyTheActivities() {
		final var activity = new ProcessActivity().activityType("RECONCILIATION");

		final var report = ProcessStateReport.failed("INCIDENT", "Timeout").atActivity("review_phase").withActivities(List.of(activity));

		assertThat(report.activities()).containsExactly(activity);
		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.currentActivityId()).isEqualTo("review_phase");
		assertThat(report.error().getCode()).isEqualTo("INCIDENT");
	}

	@Test
	void withAwaitingSignalsReplacesOnlyTheSignals() {
		final var signal = new AwaitingSignal("review_completed", "Review completed");

		final var report = ProcessStateReport.waiting("review_phase", "Review").withAwaitingSignals(List.of(signal));

		assertThat(report.awaitingSignals()).containsExactly(signal);
		assertThat(report.status()).isEqualTo(WAITING);
		assertThat(report.currentActivityId()).isEqualTo("review_phase");
		assertThat(report.activities()).isEmpty();
	}

	@Test
	void withVariablesReplacesOnlyTheVariables() {
		final var report = ProcessStateReport.completed().withVariables(Map.of("decision", "APPROVED"));

		assertThat(report.variables()).isEqualTo(Map.of("decision", "APPROVED"));
		assertThat(report.status()).isEqualTo(COMPLETED);
	}
}
