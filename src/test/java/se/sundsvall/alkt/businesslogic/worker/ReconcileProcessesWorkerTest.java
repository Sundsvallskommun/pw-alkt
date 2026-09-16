package se.sundsvall.alkt.businesslogic.worker;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

	/** The next cycle is the retry. An incident would only leave an instance standing in the engine. */
	@Test
	void completesTheTaskEvenWhenTheSweepFails() {
		doThrow(new IllegalStateException("Boom")).when(processReconciliationServiceMock).reconcile();

		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoMoreInteractions(externalTaskServiceMock);
		assertThat(RequestId.get()).isNull();
	}
}
