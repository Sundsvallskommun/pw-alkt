package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT;

@ExtendWith(MockitoExtension.class)
class AssignRestaurantNumberWorkerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "errand-id";

	@Mock
	private ProcessReportService processReportServiceMock;

	@Mock
	private FailureHandler failureHandlerMock;

	@Mock
	private RestaurantNumberService restaurantNumberServiceMock;

	@Mock
	private ExternalTask externalTaskMock;

	@Mock
	private ExternalTaskService externalTaskServiceMock;

	@InjectMocks
	private AssignRestaurantNumberWorker worker;

	@ParameterizedTest
	@CsvSource({
		"true, Restaurant number '22810001' assigned",
		"false, Restaurant number '22810001' was already assigned"
	})
	void assignsTheNumberTheProcessResolved(final boolean assigned, final String logMessage) {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER)).thenReturn("22810001");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT)).thenReturn("assignment-id");
		when(externalTaskMock.getActivityId()).thenReturn("external_task_assign_restaurant_number");
		when(restaurantNumberServiceMock.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001", "assignment-id")).thenReturn(assigned);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_assign_restaurant_number", null).withLogMessage(logMessage));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}
}
