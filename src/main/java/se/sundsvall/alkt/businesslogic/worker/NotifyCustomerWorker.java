package se.sundsvall.alkt.businesslogic.worker;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.CustomerMessageService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MESSAGE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "NotifyCustomerTask", lockDuration = NotifyCustomerWorker.LOCK_DURATION_IN_MILLISECONDS)
public class NotifyCustomerWorker extends AbstractTaskWorker {

	// Covers every call of a run timing out. A lock that expires mid-run lets another pod read the conversation before
	// this run has written to it, and the customer gets the message twice.
	static final long LOCK_DURATION_IN_MILLISECONDS = 5 * 60 * 1000L;

	private final CustomerMessageService customerMessageService;

	NotifyCustomerWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final CustomerMessageService customerMessageService) {
		super(processReportService, failureHandler);
		this.customerMessageService = customerMessageService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final String message = Optional.ofNullable(externalTask.<String>getVariable(PROCESS_VARIABLE_MESSAGE))
			.filter(StringUtils::isNotBlank)
			.orElseThrow(() -> new NonRetryableException("Step '%s' has no input parameter '%s' naming the message to send"
				.formatted(externalTask.getActivityId(), PROCESS_VARIABLE_MESSAGE)));

		if (customerMessageService.sendMessage(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask), message)) {
			logInfo("Message {} was sent to the customer of errand {}", sanitizeForLogging(message), sanitizeForLogging(getErrandId(externalTask)));
		} else {
			logInfo("Message {} had already been sent to the customer of errand {}", sanitizeForLogging(message), sanitizeForLogging(getErrandId(externalTask)));
		}

		return ProcessStateReport.running(externalTask.getActivityId(), null);
	}
}
