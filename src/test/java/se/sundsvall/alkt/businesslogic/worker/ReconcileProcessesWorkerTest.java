package se.sundsvall.alkt.businesslogic.worker;

import java.time.Duration;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconcileProcessesWorkerTest {

	@Mock
	private ProcessReconciliationService processReconciliationServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	private ReconcileProcessesWorker worker;

	@BeforeEach
	void setUp() {
		worker = new ReconcileProcessesWorker(processReconciliationServiceMock, new ReconciliationProperties(Duration.ofHours(2), Duration.ofDays(7)));
	}

	@Test
	void sweepsTheShortWindowForTheFiveMinuteReconciliation() {
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("process-reconciliation");

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile(Duration.ofHours(2));
		verifyNoMoreInteractions(processReconciliationServiceMock);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void sweepsTheNightlyWindowForTheNightlyReconciliation() {
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("process-reconciliation-nightly");

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile(Duration.ofDays(7));
		verifyNoMoreInteractions(processReconciliationServiceMock);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}

	/** The next cycle is the retry. An incident would only leave an instance standing in the engine. */
	@ParameterizedTest
	@ValueSource(strings = {
		"process-reconciliation", "process-reconciliation-nightly"
	})
	void completesTheTaskEvenWhenTheSweepFails(final String processKey) {
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(processKey);
		doThrow(new IllegalStateException("Support Management is down")).when(processReconciliationServiceMock).reconcile(any());

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}
}
