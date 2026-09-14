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
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock)).isEqualTo(ProcessStateReport.completed());
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void executeReportsOnceAndCompletesTheTask() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		verify(processReportServiceMock).report(externalTaskMock, ProcessStateReport.completed());
		verify(externalTaskServiceMock).complete(externalTaskMock);
		verifyNoInteractions(failureHandlerMock);
	}
}
