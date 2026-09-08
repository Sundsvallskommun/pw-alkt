package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
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
	void executeReportsRunningThenCompletedAndCompletesTheTask() {
		worker.execute(externalTaskMock, externalTaskServiceMock);

		final var order = inOrder(processReportServiceMock, externalTaskServiceMock);
		order.verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.running(null, null));
		order.verify(processReportServiceMock).reportProcessState(externalTaskMock, ProcessStateReport.completed());
		order.verify(externalTaskServiceMock).complete(externalTaskMock, Map.of());
		verifyNoInteractions(failureHandlerMock);
	}
}
