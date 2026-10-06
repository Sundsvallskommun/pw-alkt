package se.sundsvall.alkt.businesslogic.worker;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE;

@ExtendWith(MockitoExtension.class)
class ResolveRestaurantNumberWorkerTest {

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

	@Captor
	private ArgumentCaptor<Consumer<List<String>>> saveCaptor;

	@InjectMocks
	private ResolveRestaurantNumberWorker worker;

	@BeforeEach
	void setUp() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(externalTaskMock.getActivityId()).thenReturn("external_task_resolve_restaurant_number");
	}

	@Test
	void handsTheNumberToTheEngine() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE)).thenReturn(null);
		when(restaurantNumberServiceMock.resolveRestaurantNumber(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), isNull(), any())).thenReturn("22810001");

		final var result = worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);

		assertThat(result).isEqualTo(ProcessStateReport.running("external_task_resolve_restaurant_number", null)
			.withVariables(Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBER, "22810001"))
			.withLogMessage("Restaurant number '22810001' resolved"));
		verifyNoInteractions(failureHandlerMock, processReportServiceMock);
	}

	@Test
	void savesTheFreeNumbersInTheEngineAtOnce() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE)).thenReturn(null);
		when(restaurantNumberServiceMock.resolveRestaurantNumber(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), isNull(), saveCaptor.capture())).thenReturn("22810003");

		worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock);
		saveCaptor.getValue().accept(List.of("22810001", "22810002"));

		verify(externalTaskServiceMock).setVariables(externalTaskMock, Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE, "22810001,22810002"));
	}

	/** An empty list is saved as an empty string, which must come back as no free numbers rather than as none saved. */
	@Test
	void handsTheFreeNumbersAnEarlierRunSavedToTheService() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE)).thenReturn("");
		when(restaurantNumberServiceMock.resolveRestaurantNumber(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(List.of()), any())).thenReturn("22810001");

		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock).variables()).containsEntry(PROCESS_VARIABLE_RESTAURANT_NUMBER, "22810001");
	}

	@Test
	void splitsTheSavedNumbers() {
		when(externalTaskMock.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE)).thenReturn("22810001,22810002");
		when(restaurantNumberServiceMock.resolveRestaurantNumber(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq(ERRAND_ID), eq(List.of("22810001", "22810002")), any()))
			.thenReturn("22810003");

		assertThat(worker.executeBusinessLogic(externalTaskMock, externalTaskServiceMock).variables()).containsEntry(PROCESS_VARIABLE_RESTAURANT_NUMBER, "22810003");
	}
}
