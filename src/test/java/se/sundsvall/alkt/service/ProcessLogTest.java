package se.sundsvall.alkt.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.ProcessLogProperties;
import se.sundsvall.alkt.configuration.ProcessLogProperties.PhaseTexts;
import se.sundsvall.alkt.configuration.ProcessLogProperties.ProcessTexts;
import se.sundsvall.alkt.configuration.ProcessLogProperties.StepTexts;
import se.sundsvall.alkt.integration.operaton.OperatonIntegration;
import se.sundsvall.alkt.service.ProcessLog.Outcome;
import se.sundsvall.dept44.requestid.RequestId;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessLogTest {

	private static final String DEFINITION_ID = "alcohol-serving:1:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a";
	private static final String ACTIVITY_ID = "external_task_create_asset";

	@Mock
	private OperatonIntegration operatonIntegrationMock;

	@Mock
	private ExternalTask externalTaskMock;

	private ProcessLog processLog;

	@BeforeEach
	void setUp() {
		RequestId.init("request-id");
		processLog = new ProcessLog(new ProcessLogProperties(
			Map.of(ACTIVITY_ID, new StepTexts("Tillståndet har registrerats", "Nytt försök görs", "Processen har stannat", "Hoppades över", "Avvisades")),
			Map.of("review_phase", new PhaseTexts("Granskning har påbörjats")),
			new ProcessTexts("Avstämd som avslutad", "Avstämd som avbruten")), operatonIntegrationMock, Clock.systemUTC());
	}

	@AfterEach
	void tearDown() {
		RequestId.reset();
	}

	@Test
	void taskDone() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		final var entry = processLog.taskDone(externalTaskMock, "Asset 'asset-id' found or created");

		assertThat(entry.getActivityType()).isEqualTo("TASK");
		assertThat(entry.getActivityId()).isEqualTo(ACTIVITY_ID + "#done");
		assertThat(entry.getActivityName()).isEqualTo("Tillståndet har registrerats");
		assertThat(entry.getSeverity()).isEqualTo("INFO");
		assertThat(entry.getErrorCode()).isNull();
		assertThat(entry.getMessage()).isEqualTo("Asset 'asset-id' found or created, x-request-id request-id");
		assertThat(entry.getOccurredAt()).isCloseTo(OffsetDateTime.now(), within(5, SECONDS));
		verifyNoInteractions(operatonIntegrationMock);
	}

	/** The time goes into the id, since Support Management keeps one entry per task and id. */
	@ParameterizedTest
	@CsvSource({
		"RETRY, WARN, RETRY, Nytt försök görs",
		"FAILED, ERROR, INCIDENT, Processen har stannat",
		"SKIPPED, WARN, SKIPPED, Hoppades över"
	})
	void taskFailed(final Outcome outcome, final String severity, final String errorCode, final String name) {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		final var entry = processLog.taskFailed(externalTaskMock, outcome, "ServerProblem 502 from party-assets. Attempt 2 of 4, x-request-id request-id");

		assertThat(entry.getActivityType()).isEqualTo("TASK");
		assertThat(entry.getActivityId()).isEqualTo(ACTIVITY_ID + "#" + entry.getOccurredAt().toInstant().toEpochMilli());
		assertThat(entry.getActivityName()).isEqualTo(name);
		assertThat(entry.getSeverity()).isEqualTo(severity);
		assertThat(entry.getErrorCode()).isEqualTo(errorCode);
		assertThat(entry.getMessage()).isEqualTo("ServerProblem 502 from party-assets. Attempt 2 of 4, x-request-id request-id");
	}

	/**
	 * A retry from Cockpit runs the same task again with the retries set anew, so the attempt count repeats. Two failures
	 * of one task must still be two entries.
	 */
	@Test
	void everyFailureOfATaskGetsAnIdOfItsOwn() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		final var incident = processLog.taskFailed(externalTaskMock, Outcome.FAILED, "Attempt 4 of 4");

		// A later failure is at least a millisecond later, which is all the id needs to tell the two apart
		await().atMost(Duration.ofSeconds(1))
			.until(() -> !processLog.taskFailed(externalTaskMock, Outcome.FAILED, "Attempt 4 of 4").getActivityId().equals(incident.getActivityId()));
	}

	@Test
	void taskRejected() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(externalTaskMock.getId()).thenReturn("external-task-id");

		final var entry = processLog.taskRejected(externalTaskMock, "No deficiencies, no action errand created");

		assertThat(entry.getActivityType()).isEqualTo("TASK");
		assertThat(entry.getActivityId()).isEqualTo(ACTIVITY_ID + "#rejected#external-task-id");
		assertThat(entry.getActivityName()).isEqualTo("Avvisades");
		assertThat(entry.getSeverity()).isEqualTo("WARN");
		assertThat(entry.getErrorCode()).isEqualTo("REJECTED");
		assertThat(entry.getMessage()).isEqualTo("No deficiencies, no action errand created, x-request-id request-id");
		assertThat(entry.getOccurredAt()).isCloseTo(OffsetDateTime.now(), within(5, SECONDS));
		verifyNoInteractions(operatonIntegrationMock);
	}

	/** A new choice is a new external task and an entry of its own; a replay of the same task is the same entry. */
	@Test
	void everyRejectedRunGetsAnIdOfItsOwnAndAReplayKeepsIt() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(externalTaskMock.getId()).thenReturn("first-task", "first-task", "second-task");

		final var first = processLog.taskRejected(externalTaskMock, "message");
		final var replay = processLog.taskRejected(externalTaskMock, "message");
		final var second = processLog.taskRejected(externalTaskMock, "message");

		assertThat(replay.getActivityId()).isEqualTo(first.getActivityId());
		assertThat(second.getActivityId()).isNotEqualTo(first.getActivityId());
	}

	/**
	 * Reported with the same external task id, the reconciliation's incident must not make Support Management drop the done
	 * entry.
	 */
	@Test
	void taskDoneAfterReconciledIncidentGetsAnIdOfItsOwn() {
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);

		final var incident = processLog.incident(DEFINITION_ID, ACTIVITY_ID, "Timeout", null);
		final var done = processLog.taskDone(externalTaskMock, "Asset 'asset-id' found or created");

		assertThat(done.getActivityId()).isNotEqualTo(incident.getActivityId());
	}

	@Test
	void taskWithoutTextIsNamedAfterTheModel() {
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_decision");
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.labelOf(DEFINITION_ID, "external_task_check_decision")).thenReturn("Check decision");

		assertThat(processLog.taskDone(externalTaskMock, "Decision outcome APPROVAL").getActivityName()).isEqualTo("Check decision");
	}

	/** A step the model cannot skip has no text for it, and is named after the model should it happen anyway. */
	@Test
	void outcomeWithoutTextIsNamedAfterTheModel() {
		processLog = new ProcessLog(new ProcessLogProperties(
			Map.of(ACTIVITY_ID, new StepTexts("Klart", "Nytt försök", "Stannat", null, null)),
			Map.of(),
			new ProcessTexts("Avslutad", "Avbruten")), operatonIntegrationMock, Clock.systemUTC());
		when(externalTaskMock.getActivityId()).thenReturn(ACTIVITY_ID);
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
		when(operatonIntegrationMock.labelOf(DEFINITION_ID, ACTIVITY_ID)).thenReturn("Create asset");

		assertThat(processLog.taskFailed(externalTaskMock, Outcome.SKIPPED, "message").getActivityName()).isEqualTo("Create asset");
		assertThat(processLog.phaseEntered("review_phase", "Review").getActivityName()).isEqualTo("Review");
	}

	@Test
	void taskWithoutTextOrNameIsNamedAfterItsId() {
		when(externalTaskMock.getActivityId()).thenReturn("external_task_check_decision");
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);

		assertThat(processLog.taskDone(externalTaskMock, "Decision outcome APPROVAL").getActivityName()).isEqualTo("external_task_check_decision");
	}

	@Test
	void phaseEntered() {
		final var entry = processLog.phaseEntered("review_phase", "Review");

		assertThat(entry.getActivityType()).isEqualTo("PHASE");
		assertThat(entry.getActivityId()).isEqualTo("review_phase");
		assertThat(entry.getActivityName()).isEqualTo("Granskning har påbörjats");
		assertThat(entry.getSeverity()).isEqualTo("INFO");
		assertThat(entry.getMessage()).isEqualTo("Phase 'review_phase' entered");
	}

	@Test
	void phaseWithoutTextKeepsTheNameOfTheModel() {
		assertThat(processLog.phaseEntered("decision_phase", "Decision").getActivityName()).isEqualTo("Decision");
		assertThat(processLog.phaseEntered("decision_phase", null).getActivityName()).isEqualTo("decision_phase");
	}

	/** The id stays the activity alone, so an incident the log already holds is not written again. */
	@Test
	void incident() {
		final var occurredAt = OffsetDateTime.now().minusMinutes(5);

		final var entry = processLog.incident(DEFINITION_ID, ACTIVITY_ID, "Timeout", occurredAt);

		assertThat(entry.getActivityType()).isEqualTo("INCIDENT");
		assertThat(entry.getActivityId()).isEqualTo(ACTIVITY_ID);
		assertThat(entry.getActivityName()).isEqualTo("Processen har stannat");
		assertThat(entry.getSeverity()).isEqualTo("ERROR");
		assertThat(entry.getErrorCode()).isEqualTo("INCIDENT");
		assertThat(entry.getMessage()).isEqualTo("Timeout");
		assertThat(entry.getOccurredAt()).isEqualTo(occurredAt);
	}

	@Test
	void incidentWithoutTimestampHappenedNow() {
		assertThat(processLog.incident(DEFINITION_ID, ACTIVITY_ID, "Timeout", null).getOccurredAt()).isCloseTo(OffsetDateTime.now(), within(5, SECONDS));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		"SETTLED_COMPLETED, WARN, null, Avstämd som avslutad",
		"SETTLED_TERMINATED, ERROR, TERMINATED, Avstämd som avbruten"
	})
	void settled(final Outcome outcome, final String severity, final String errorCode, final String name) {
		final var entry = processLog.settled(outcome, "Ended without reporting it", null);

		assertThat(entry.getActivityType()).isEqualTo("RECONCILIATION");
		assertThat(entry.getActivityId()).isNull();
		assertThat(entry.getActivityName()).isEqualTo(name);
		assertThat(entry.getSeverity()).isEqualTo(severity);
		assertThat(entry.getErrorCode()).isEqualTo(errorCode);
		assertThat(entry.getMessage()).isEqualTo("Ended without reporting it");
		assertThat(entry.getOccurredAt()).isNotNull();
	}

	/** A value longer than Support Management accepts costs the whole report, so it is cut. */
	@Test
	void cutsWhatIsTooLong() {
		when(externalTaskMock.getActivityId()).thenReturn("a".repeat(300));
		when(externalTaskMock.getProcessDefinitionId()).thenReturn(DEFINITION_ID);

		final var entry = processLog.taskFailed(externalTaskMock, Outcome.RETRY, "m".repeat(3000));

		assertThat(entry.getActivityId()).hasSize(255);
		assertThat(entry.getActivityName()).hasSize(255);
		assertThat(entry.getMessage()).hasSize(2048);
	}
}
