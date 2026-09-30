package se.sundsvall.alkt.businesslogic.handler;

import generated.se.sundsvall.supportmanagement.ProcessActivity;
import java.util.List;
import java.util.UUID;
import org.camunda.bpm.client.exception.NotFoundException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.messaging.MessagingIntegration;
import se.sundsvall.alkt.service.ProcessLog;
import se.sundsvall.alkt.service.ProcessLog.Outcome;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.BPMN_ERROR_STEP_SKIPPED;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_RETRY;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class FailureHandlerTest {

	private static final long EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS = 10_000;
	private static final ProcessActivity ACTIVITY = new ProcessActivity().activityType("TASK");

	@Autowired
	private FailureHandler failureHandler;

	@MockitoBean
	private ExternalTaskService externalTaskServiceMock;

	@MockitoBean
	private ExternalTask externalTaskMock;

	@MockitoBean
	private ProcessReportService processReportServiceMock;

	@MockitoBean
	private ProcessLog processLogMock;

	@MockitoBean
	private MessagingIntegration messagingIntegrationMock;

	@BeforeEach
	void setUp() {
		RequestId.init("request-id");
		when(processLogMock.taskFailed(eq(externalTaskMock), any(), anyString())).thenReturn(ACTIVITY);
	}

	@AfterEach
	void tearDown() {
		RequestId.reset();
	}

	@Test
	void alertsSlackWhenTheLastRetryFails() {
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(1);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn("ALKT");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn("errand-id");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTaskMock.getActivityId()).thenReturn("external_task_create_asset");
		when(externalTaskMock.getProcessInstanceId()).thenReturn("instance-id");

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "Party assets is down");

		verify(messagingIntegrationMock).sendSlack("2281",
			"[2281][ALKT][alcohol-serving] Incident in external_task_create_asset for errand errand-id (process instance instance-id): Party assets is down. Attempt 4 of 4, x-request-id request-id");
	}

	@Test
	void handleIncidentRaisesTheIncidentAndAlertsOnTheFirstAttempt() {
		final var id = UUID.randomUUID().toString();
		final var message = "Process 'alcohol-serving' has no title for a decision made automatically";
		final var expected = message + ". Attempt 1, not retried, x-request-id request-id";
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");

		when(externalTaskMock.getRetries()).thenReturn(null);

		failureHandler.handleIncident(externalTaskServiceMock, externalTaskMock, message);

		verify(processLogMock).taskFailed(externalTaskMock, Outcome.FAILED, expected);
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.failed(ERROR_CODE_INCIDENT, expected).withActivities(List.of(ACTIVITY)));
		verify(externalTaskServiceMock).handleFailure(id, expected, null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verify(messagingIntegrationMock).sendSlack(eq("2281"), contains(expected));
	}

	@Test
	void handleSkippableFailureRetriesWhileRetriesRemain() {
		final var id = UUID.randomUUID().toString();
		final var expected = "Support Management is down. Attempt 2 of 4, x-request-id request-id";
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(3);

		assertThat(failureHandler.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "Support Management is down", true)).isFalse();

		verify(processLogMock).taskFailed(externalTaskMock, Outcome.RETRY, expected);
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.retrying(ERROR_CODE_RETRY, expected).withActivities(List.of(ACTIVITY)));
		verify(externalTaskServiceMock).handleFailure(id, expected, null, 2, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verify(externalTaskServiceMock, never()).handleBpmnError(any(ExternalTask.class), any(), any());
		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void handleSkippableFailureThrowsTheSkipErrorAndAlertsWhenTheLastRetryFails() {
		final var expected = "Support Management is down. Attempt 4 of 4, x-request-id request-id";
		when(externalTaskMock.getRetries()).thenReturn(1);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn("ALKT");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn("errand-id");
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("alcohol-serving");
		when(externalTaskMock.getActivityId()).thenReturn("external_task_notify_processing_started");
		when(externalTaskMock.getProcessInstanceId()).thenReturn("instance-id");

		assertThat(failureHandler.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "Support Management is down", true)).isTrue();

		verify(externalTaskServiceMock).handleBpmnError(externalTaskMock, BPMN_ERROR_STEP_SKIPPED, expected);
		verify(externalTaskServiceMock, never()).handleFailure(nullable(String.class), any(), any(), anyInt(), anyLong());
		verify(processLogMock).taskFailed(externalTaskMock, Outcome.SKIPPED, expected);
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.running(null, null).withActivities(List.of(ACTIVITY)));
		verify(messagingIntegrationMock).sendSlack("2281",
			"[2281][ALKT][alcohol-serving] Skipped external_task_notify_processing_started for errand errand-id (process instance instance-id): " + expected);
	}

	@Test
	void handleSkippableFailureSkipsOnTheFirstAttemptWhenNoRetryCanFixIt() {
		final var expected = "No text is configured. Attempt 1, not retried, x-request-id request-id";
		when(externalTaskMock.getRetries()).thenReturn(null);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");

		assertThat(failureHandler.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "No text is configured", false)).isTrue();

		verify(externalTaskServiceMock).handleBpmnError(externalTaskMock, BPMN_ERROR_STEP_SKIPPED, expected);
		verify(processLogMock).taskFailed(externalTaskMock, Outcome.SKIPPED, expected);
		verify(messagingIntegrationMock).sendSlack(eq("2281"), contains(expected));
	}

	@Test
	void handleSkippableFailureLeavesATaskThatIsGoneWithoutAnAlert() {
		doThrow(mock(NotFoundException.class)).when(externalTaskServiceMock).handleBpmnError(eq(externalTaskMock), eq(BPMN_ERROR_STEP_SKIPPED), anyString());

		assertThat(failureHandler.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "message", false)).isFalse();

		verifyNoInteractions(processReportServiceMock, messagingIntegrationMock);
	}

	@Test
	void alertsEvenWhenTheSkipCannotBeReported() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Bad Gateway")).when(processReportServiceMock).report(eq(externalTaskMock), any());

		assertThat(failureHandler.handleSkippableFailure(externalTaskServiceMock, externalTaskMock, "message", false)).isTrue();

		verify(messagingIntegrationMock).sendSlack(eq("2281"), contains("message"));
	}

	/** A task that is gone was taken away by a cancellation, so there is no failure to report or alert on. */
	@Test
	void leavesATaskThatIsGoneWithoutAReportOrAnAlert() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		doThrow(mock(NotFoundException.class)).when(externalTaskServiceMock).handleFailure(eq(id), anyString(), eq(null), eq(0), eq(EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS));

		assertThatNoException().isThrownBy(() -> failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message"));

		verifyNoInteractions(processReportServiceMock, messagingIntegrationMock);
	}

	@Test
	void doesNotAlertWhenTheEngineWasNotToldAboutTheIncident() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		doThrow(new IllegalStateException("Lock expired")).when(externalTaskServiceMock).handleFailure(eq(id), anyString(), eq(null), eq(0), eq(EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS));

		assertThatThrownBy(() -> failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message"))
			.isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void doesNotAlertWhileRetriesRemain() {
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(2);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void tellsTheEngineEvenWhenTheAlertFails() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Bad Gateway")).when(messagingIntegrationMock).sendSlack(any(), any());

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(externalTaskServiceMock).handleFailure(id, "message. Attempt 4 of 4, x-request-id request-id", null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
	}

	@Test
	void reportsRetryingWhileRetriesRemain() {
		final var expected = "message. Attempt 3 of 4, x-request-id request-id";
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(2);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(processLogMock).taskFailed(externalTaskMock, Outcome.RETRY, expected);
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.retrying(ERROR_CODE_RETRY, expected).withActivities(List.of(ACTIVITY)));
	}

	@Test
	void reportsFailedOnTheLastRetry() {
		final var expected = "message. Attempt 4 of 4, x-request-id request-id";
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(1);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(processLogMock).taskFailed(externalTaskMock, Outcome.FAILED, expected);
		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.failed(ERROR_CODE_INCIDENT, expected).withActivities(List.of(ACTIVITY)));
	}

	@Test
	void alertsEvenWhenTheReportFails() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Bad Gateway")).when(processReportServiceMock).report(eq(externalTaskMock), any());

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(externalTaskServiceMock).handleFailure(id, "message. Attempt 4 of 4, x-request-id request-id", null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verify(messagingIntegrationMock).sendSlack(eq("2281"), contains("message"));
	}

	/** The engine holds no retries before the first failure, and the retries left after each one. */
	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		"null, 1, 3",
		"3, 2, 2",
		"2, 3, 1",
		"1, 4, 0",
		"9, 1, 8",
	})
	void countsTheAttempts(final Integer retries, final int attempt, final int retriesLeft) {
		final var id = UUID.randomUUID().toString();
		final var expected = "message. Attempt %d of 4, x-request-id request-id".formatted(attempt);
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(retries);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(externalTaskServiceMock).handleFailure(id, expected, null, retriesLeft, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}

	/**
	 * The worker id must not be passed to {@code handleFailure} - the second parameter is the error message, which
	 * becomes the incident message once the retries are exhausted.
	 */
	@Test
	void doesNotUseWorkerIdAsErrorMessage() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message");

		verify(externalTaskMock, never()).getWorkerId();
		verify(externalTaskServiceMock).handleFailure(id, "message. Attempt 4 of 4, x-request-id request-id", null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}
}
