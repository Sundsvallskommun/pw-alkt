package se.sundsvall.alkt.businesslogic.worker;

import java.util.UUID;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.service.ProcessReconciliationService;
import se.sundsvall.dept44.requestid.RequestId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

	@InjectMocks
	private ReconcileProcessesWorker worker;

	@Test
	void completesTheTaskAfterASweep() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReconciliationServiceMock).reconcile();
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}

	/** No retries: the next cycle is a new attempt, and an incident on the run is what makes the failure visible. */
	@Test
	void raisesAnIncidentWhenTheSweepFails() {
		final var id = UUID.randomUUID().toString();
		when(externalTaskMock.getId()).thenReturn(id);
		doThrow(new IllegalStateException("Boom")).when(processReconciliationServiceMock).reconcile();

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(externalTaskServiceMock).handleFailure(id, "Boom", null, 0, 0);
		verify(externalTaskServiceMock, never()).complete(any());
		assertThat(RequestId.get()).isNull();
	}
}
