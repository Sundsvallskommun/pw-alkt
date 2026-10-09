package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("FindRestaurantNumberTask")
public class FindRestaurantNumberWorker extends AbstractTaskWorker {

	private final RestaurantNumberService restaurantNumberService;

	FindRestaurantNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final RestaurantNumberService restaurantNumberService) {
		super(processReportService, failureHandler);
		this.restaurantNumberService = restaurantNumberService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var restaurantNumber = restaurantNumberService.findRestaurantNumberOfHolder(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask));

		logInfo("Errand {} has restaurant number {} of its permit holder", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(restaurantNumber));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBER, restaurantNumber))
			.withLogMessage("Restaurant number '%s' found".formatted(restaurantNumber));
	}
}
