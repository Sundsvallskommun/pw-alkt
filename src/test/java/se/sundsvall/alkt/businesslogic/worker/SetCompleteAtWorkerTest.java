package se.sundsvall.alkt.businesslogic.worker;

import java.time.LocalDate;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

@ExtendWith(MockitoExtension.class)
class SetCompleteAtWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private ErrandService errandServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private SetCompleteAtWorker worker;

	@Test
	void setsTheDayTheErrandWasComplete() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_set_complete_at");
		when(errandServiceMock.setCompleteAt(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)).thenReturn(LocalDate.of(2026, 10, 20));

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_set_complete_at", null).withLogMessage("Complete at 2026-10-20"));
		verify(errandServiceMock).setCompleteAt(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
