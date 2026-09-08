package se.sundsvall.alkt.api.model;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;
import static se.sundsvall.alkt.api.model.ProcessStatus.FAILED;
import static se.sundsvall.alkt.api.model.ProcessStatus.RETRYING;
import static se.sundsvall.alkt.api.model.ProcessStatus.RUNNING;
import static se.sundsvall.alkt.api.model.ProcessStatus.WAITING;

class ProcessStateReportTest {

	@Test
	void runningCarriesTheActivityAndNoVersionOrError() {
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
	void waitingCarriesTheActivityAndNoVersionOrError() {
		final var report = ProcessStateReport.waiting("activityId", "activityName");

		assertThat(report.status()).isEqualTo(WAITING);
		assertThat(report.currentActivityId()).isEqualTo("activityId");
		assertThat(report.currentActivityName()).isEqualTo("activityName");
		assertThat(report.error()).isNull();
	}

	@Test
	void completedCarriesNoActivityVersionOrError() {
		final var report = ProcessStateReport.completed();

		assertThat(report.status()).isEqualTo(COMPLETED);
		assertThat(report.currentActivityId()).isNull();
		assertThat(report.currentActivityName()).isNull();
		assertThat(report.errandVersion()).isNull();
		assertThat(report.error()).isNull();
	}

	@Test
	void failedCarriesTheErrorCodeAndMessage() {
		final var report = ProcessStateReport.failed("CODE", "message");

		assertThat(report.status()).isEqualTo(FAILED);
		assertThat(report.error()).isEqualTo(new ProcessError("CODE", "message"));
	}

	@Test
	void retryingCarriesTheErrorCodeAndMessage() {
		final var report = ProcessStateReport.retrying("CODE", "message");

		assertThat(report.status()).isEqualTo(RETRYING);
		assertThat(report.error()).isEqualTo(new ProcessError("CODE", "message"));
	}

	@Test
	void withErrandVersionReplacesOnlyTheVersion() {
		final var report = ProcessStateReport.completed().withErrandVersion(7L);

		assertThat(report.errandVersion()).isEqualTo(7L);
		assertThat(report.status()).isEqualTo(COMPLETED);
	}

	@Test
	void withVariablesReplacesOnlyTheVariables() {
		final var report = ProcessStateReport.completed().withVariables(Map.of("decision", "APPROVED"));

		assertThat(report.variables()).isEqualTo(Map.of("decision", "APPROVED"));
		assertThat(report.status()).isEqualTo(COMPLETED);
	}

}
