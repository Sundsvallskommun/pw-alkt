package se.sundsvall.alkt.businesslogic.worker;

import java.time.Duration;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.configuration.ReconciliationProperties;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION_NIGHTLY;

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
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY_RECONCILIATION);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile(PROCESS_KEY_RECONCILIATION, Duration.ofHours(2));
		verifyNoMoreInteractions(processReconciliationServiceMock);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void sweepsTheNightlyWindowForTheNightlyReconciliation() {
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn(PROCESS_KEY_RECONCILIATION_NIGHTLY);

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile(PROCESS_KEY_RECONCILIATION_NIGHTLY, Duration.ofDays(7));
		verifyNoMoreInteractions(processReconciliationServiceMock);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}

	@Test
	void sweepsTheShortWindowForAnUnknownReconciliation() {
		when(externalTaskMock.getProcessDefinitionKey()).thenReturn("process-reconciliation-weekly");

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile("process-reconciliation-weekly", Duration.ofHours(2));
		verifyNoMoreInteractions(processReconciliationServiceMock);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
	}
}
