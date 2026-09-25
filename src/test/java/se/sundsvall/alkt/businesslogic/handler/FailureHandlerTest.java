package se.sundsvall.alkt.businesslogic.handler;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.messaging.MessagingIntegration;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.ERROR_CODE_INCIDENT;
import static se.sundsvall.alkt.Constants.ERROR_CODE_RETRY;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class FailureHandlerTest {

	private static final long EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS = 10_000;

	@Autowired
	private FailureHandler failureHandler;

	@MockitoBean
	private ExternalTaskService externalTaskServiceMock;

	@MockitoBean
	private ExternalTask externalTaskMock;

	@MockitoBean
	private ProcessReportService processReportServiceMock;

	@MockitoBean
	private MessagingIntegration messagingIntegrationMock;

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

		RequestId.init("request-id");
		try {
			failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "Party assets is down");
		} finally {
			RequestId.reset();
		}

		verify(messagingIntegrationMock).sendSlack("2281",
			"[2281][ALKT][alcohol-serving] Incident in external_task_create_asset for errand errand-id (process instance instance-id, x-request-id request-id): Party assets is down");
	}

	@Test
	void handleIncidentRaisesTheIncidentAndAlertsOnTheFirstAttempt() {
		final var id = UUID.randomUUID().toString();
		final var message = "Process 'alcohol-serving' has no title for a decision made automatically";
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn("2281");

		failureHandler.handleIncident(externalTaskServiceMock, externalTaskMock, message);

		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.failed(ERROR_CODE_INCIDENT, message));
		verify(externalTaskServiceMock).handleFailure(id, message, null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verify(messagingIntegrationMock).sendSlack(eq("2281"), contains(message));
		verify(externalTaskMock, never()).getRetries();
	}

	@Test
	void doesNotAlertWhenTheEngineWasNotToldAboutTheIncident() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		doThrow(new IllegalStateException("Lock expired")).when(externalTaskServiceMock).handleFailure(id, "message", null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);

		assertThatThrownBy(() -> failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message"))
			.isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void doesNotAlertWhileRetriesRemain() {
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(2);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message", Map.of());

		verifyNoInteractions(messagingIntegrationMock);
	}

	@Test
	void tellsTheEngineEvenWhenTheAlertFails() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Bad Gateway")).when(messagingIntegrationMock).sendSlack(any(), any());

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, "message", Map.of());

		verify(externalTaskServiceMock).handleFailure(id, "message", null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS, Map.of(), Collections.emptyMap());
	}

	@Test
	void reportsRetryingWhileRetriesRemain() {
		final var message = "message";
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(2);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.retrying(ERROR_CODE_RETRY, message));
	}

	@Test
	void reportsFailedOnTheLastRetry() {
		final var message = "message";
		when(externalTaskMock.getId()).thenReturn(UUID.randomUUID().toString());
		when(externalTaskMock.getRetries()).thenReturn(1);

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.failed(ERROR_CODE_INCIDENT, message));
	}

	/** Support Management being down must not keep the task locked: the engine is told about the failure regardless. */
	@Test
	void tellsTheEngineEvenWhenTheReportFails() {
		final var message = "message";
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(2);
		doThrow(new ClientProblem(HttpStatus.BAD_GATEWAY, "Bad Gateway")).when(processReportServiceMock).report(eq(externalTaskMock), any());

		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		verify(externalTaskServiceMock).handleFailure(id, message, null, 1, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
	}

	@Test
	void handleExceptionWithVariables() {
		// Setup
		final var message = "message";
		final var id = UUID.randomUUID().toString();
		final var retriesLeft = 2;
		final Map<String, Object> variables = Map.of("key", "value");

		// Mock
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(retriesLeft);

		// Act
		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message, variables);

		// Assert and verify
		verify(externalTaskMock).getId();
		verify(externalTaskServiceMock).handleFailure(id, message, null, retriesLeft - 1, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS, variables, Collections.emptyMap());
		verifyNoMoreInteractions(externalTaskServiceMock);
	}

	@Test
	void handleExceptionWithoutVariables() {
		// Setup
		final var message = "message";
		final var id = UUID.randomUUID().toString();
		final var retriesLeft = 2;

		// Mock
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(retriesLeft);

		// Act
		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		// Assert and verify
		verify(externalTaskMock).getId();
		verify(externalTaskServiceMock).handleFailure(id, message, null, retriesLeft - 1, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}

	@Test
	void handleExceptionWhenRetriesNotSet() {
		// Setup
		final var message = "message";
		final var id = UUID.randomUUID().toString();

		// Mock
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(null);

		// Act
		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		// Assert and verify
		verify(externalTaskMock).getId();
		verify(externalTaskServiceMock).handleFailure(id, message, null, 3, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}

	/**
	 * The worker id must not be passed to {@code handleFailure} - the second parameter is the error message, which
	 * becomes the incident message once the retries are exhausted.
	 */
	@Test
	void doesNotUseWorkerIdAsErrorMessage() {
		// Setup
		final var message = "message";
		final var id = UUID.randomUUID().toString();

		// Mock
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(1);

		// Act
		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		// Assert and verify
		verify(externalTaskMock, never()).getWorkerId();
		verify(externalTaskServiceMock).handleFailure(id, message, null, 0, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}
}
