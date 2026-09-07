package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;

@ExtendWith(MockitoExtension.class)
class CompleteProcessWorkerTest {

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private CompleteProcessWorker worker;

	@Test
	void reportsTheProcessAsCompleted() {
		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock)).isEqualTo(COMPLETED);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void executeReportsOnceAndCompletesTheTask() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock).report(externalTaskMock, COMPLETED, null);
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoInteractions(failureHandlerMock);
	}
}
