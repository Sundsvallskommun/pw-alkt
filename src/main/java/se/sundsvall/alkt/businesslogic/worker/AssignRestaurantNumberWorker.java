package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("AssignRestaurantNumberTask")
public class AssignRestaurantNumberWorker extends AbstractTaskWorker {

	private final RestaurantNumberService restaurantNumberService;

	AssignRestaurantNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final RestaurantNumberService restaurantNumberService) {
		super(processReportService, failureHandler);
		this.restaurantNumberService = restaurantNumberService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final String restaurantNumber = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER);
		final String latestAssignmentIdSeen = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT);
		final var report = ProcessStateReport.running(externalTask.getActivityId(), null);

		if (!restaurantNumberService.assignRestaurantNumber(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask), restaurantNumber, latestAssignmentIdSeen)) {
			return report.withLogMessage("Restaurant number '%s' was already assigned".formatted(restaurantNumber));
		}

		logInfo("Errand {} has restaurant number {} assigned", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(restaurantNumber));
		return report.withLogMessage("Restaurant number '%s' assigned".formatted(restaurantNumber));
	}
}
