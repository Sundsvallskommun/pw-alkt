package se.sundsvall.alkt.businesslogic.worker;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("ResolveRestaurantNumberTask")
public class ResolveRestaurantNumberWorker extends AbstractTaskWorker {

	private final RestaurantNumberService restaurantNumberService;

	ResolveRestaurantNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final RestaurantNumberService restaurantNumberService) {
		super(processReportService, failureHandler);
		this.restaurantNumberService = restaurantNumberService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final String availableBeforeCreate = externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE);
		final var resolved = restaurantNumberService.resolveRestaurantNumber(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			Optional.ofNullable(availableBeforeCreate).map(ResolveRestaurantNumberWorker::toNumbers).orElse(null),
			// Why: written at once rather than on completion, so it outlives a run that fails after the create.
			numbers -> externalTaskService.setVariables(externalTask, Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE, String.join(",", numbers))));

		logInfo("Errand {} gets restaurant number {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(resolved.number()));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBER, resolved.number(), PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT, resolved.latestAssignmentId()))
			.withLogMessage("Restaurant number '%s' resolved".formatted(resolved.number()));
	}

	private static List<String> toNumbers(final String commaSeparated) {
		return Arrays.stream(commaSeparated.split(","))
			.filter(StringUtils::isNotBlank)
			.toList();
	}
}
