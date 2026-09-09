package se.sundsvall.alkt.businesslogic.handler;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.service.ProcessReportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
		verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.retrying("TASK_FAILED", message));
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
		verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.retrying("TASK_FAILED", message));
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
		verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.retrying("TASK_FAILED", message));
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
		verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.failed("TASK_FAILED", message));
	}

	/**
	 * A Support Management outage while reporting the failure must not stop handleFailure from running -
	 * that would leave the task locked with its retries never decremented.
	 */
	@Test
	void handleExceptionStillHandlesFailureWhenReportingFails() {
		// Setup
		final var message = "message";
		final var id = UUID.randomUUID().toString();
		final var retriesLeft = 2;

		// Mock
		when(externalTaskMock.getId()).thenReturn(id);
		when(externalTaskMock.getRetries()).thenReturn(retriesLeft);
		doThrow(new RuntimeException("boom")).when(processReportServiceMock).reportProcessState(any(), any());

		// Act
		failureHandler.handleException(externalTaskServiceMock, externalTaskMock, message);

		// Assert and verify
		verify(externalTaskServiceMock).handleFailure(id, message, null, retriesLeft - 1, EXPECTED_RETRY_TIMEOUT_IN_MILLISECONDS);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}
}
