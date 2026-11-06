package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID;
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
		"true, false, Restaurant number '22810001' assigned",
		"false, false, Restaurant number '22810001' was already assigned",
		"true, true, 'Restaurant number ''22810001'' assigned, written to the decision'",
		"false, true, 'Restaurant number ''22810001'' was already assigned, written to the decision'"
	})
	void assignsTheNumberTheProcessResolvedAndRecordsItInTheDecision(final boolean assigned, final boolean recorded, final String logMessage) {
		givenTheTask();
		when(restaurantNumberServiceMock.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001", "address-id", "assignment-id")).thenReturn(assigned);
		when(restaurantNumberServiceMock.recordNewRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001")).thenReturn(recorded);

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_assign_restaurant_number", null).withLogMessage(logMessage));
		final var inOrder = inOrder(restaurantNumberServiceMock);
		inOrder.verify(restaurantNumberServiceMock).assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001", "address-id", "assignment-id");
		inOrder.verify(restaurantNumberServiceMock).recordNewRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001");
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	/** The decision must not name a number the holder was never given. */
	@Test
	void recordsNothingInTheDecisionWhenTheAssignmentFails() {
		givenTheTask();
		final var failure = new NonRetryableException("Restaurant number '22810001' was assigned by another errand");
		when(restaurantNumberServiceMock.assignRestaurantNumber(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "22810001", "address-id", "assignment-id")).thenThrow(failure);

		assertThatThrownBy(() -> worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock)).isSameAs(failure);

		verify(restaurantNumberServiceMock, never()).recordNewRestaurantNumber(any(), any(), any(), any());
	}

	private void givenTheTask() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER)).thenReturn("22810001");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID)).thenReturn("address-id");
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT)).thenReturn("assignment-id");
		lenient().when(externalTaskMock.getActivityId()).thenReturn("external_task_assign_restaurant_number");
	}
}
