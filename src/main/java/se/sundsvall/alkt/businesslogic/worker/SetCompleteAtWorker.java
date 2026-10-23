package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "SetCompleteAtTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class SetCompleteAtWorker extends AbstractTaskWorker {

	private final ErrandService errandService;

	SetCompleteAtWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final ErrandService errandService) {
		super(processReportService, failureHandler);
		this.errandService = errandService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var completeAt = errandService.setCompleteAt(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getCreateTime().toInstant());

		logInfo("Errand {} was complete at {}", sanitizeForLogging(getErrandId(externalTask)), completeAt);

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Complete at %s".formatted(completeAt));
	}
}
