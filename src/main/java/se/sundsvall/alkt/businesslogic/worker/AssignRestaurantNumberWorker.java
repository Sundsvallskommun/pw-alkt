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
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "AssignRestaurantNumberTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class AssignRestaurantNumberWorker extends AbstractTaskWorker {

	private final RestaurantNumberService restaurantNumberService;

	AssignRestaurantNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final RestaurantNumberService restaurantNumberService) {
		super(processReportService, failureHandler);
		this.restaurantNumberService = restaurantNumberService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final String restaurantNumber = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER);
		final String addressId = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID);
		final String latestAssignmentIdSeen = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT);
		final var municipalityId = getMunicipalityId(externalTask);
		final var namespace = getNamespace(externalTask);
		final var errandId = getErrandId(externalTask);

		final var assigned = restaurantNumberService.assignRestaurantNumber(municipalityId, namespace, errandId, restaurantNumber, addressId, latestAssignmentIdSeen);
		if (assigned) {
			logInfo("Errand {} has restaurant number {} assigned", sanitizeForLogging(errandId), sanitizeForLogging(restaurantNumber));
		}

		// Only once the number is assigned, so that the decision never names a number the holder does not have. A rerun
		// after a failed write finds the number assigned and writes again.
		final var recorded = restaurantNumberService.recordNewRestaurantNumber(municipalityId, namespace, errandId, restaurantNumber);
		if (recorded) {
			logInfo("Errand {} has restaurant number {} written to its decision", sanitizeForLogging(errandId), sanitizeForLogging(restaurantNumber));
		}

		return running(externalTask).withLogMessage("Restaurant number '%s' %s%s".formatted(restaurantNumber, assigned ? "assigned" : "was already assigned",
			recorded ? ", written to the decision" : ""));
	}
}
