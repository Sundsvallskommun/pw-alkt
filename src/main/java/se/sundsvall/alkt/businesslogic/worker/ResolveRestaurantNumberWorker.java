package se.sundsvall.alkt.businesslogic.worker;

import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.RestaurantNumberService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.RestaurantNumbersBeforeCreate;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "ResolveRestaurantNumberTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class ResolveRestaurantNumberWorker extends AbstractTaskWorker {

	static final String PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE = "restaurantNumbersBeforeCreate";
	static final String PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE_ADDRESS_ID = "restaurantNumbersBeforeCreateAddressId";

	private final RestaurantNumberService restaurantNumberService;

	ResolveRestaurantNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final RestaurantNumberService restaurantNumberService) {
		super(processReportService, failureHandler);
		this.restaurantNumberService = restaurantNumberService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var resolved = restaurantNumberService.resolveRestaurantNumber(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			toBeforeCreate(externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE_ADDRESS_ID), externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE)),
			beforeCreate -> externalTaskService.setVariables(externalTask, Map.of(
				PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE_ADDRESS_ID, beforeCreate.addressId(),
				PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE, String.join(",", beforeCreate.numbers()))));

		logInfo("Errand {} gets restaurant number {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(resolved.number()));

		return running(externalTask)
			.withVariables(Map.of(PROCESS_VARIABLE_RESTAURANT_NUMBER, resolved.number(), PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID, resolved.addressId(),
				PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT, resolved.latestAssignmentId()))
			.withLogMessage("Restaurant number '%s' resolved".formatted(resolved.number()));
	}

	/** What an earlier run saved, or null when it saved nothing, or saved the numbers without their address. */
	private static RestaurantNumbersBeforeCreate toBeforeCreate(final String addressId, final String commaSeparatedNumbers) {
		if (addressId == null || commaSeparatedNumbers == null) {
			return null;
		}
		return new RestaurantNumbersBeforeCreate(addressId, Arrays.stream(commaSeparatedNumbers.split(","))
			.filter(StringUtils::isNotBlank)
			.toList());
	}
}
