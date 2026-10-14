package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ActionErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateActionErrandTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateActionErrandWorker extends AbstractTaskWorker {

	static final String PROCESS_VARIABLE_ACTION_ERRAND_CREATED = "actionErrandCreated";

	private final ActionErrandService actionErrandService;

	CreateActionErrandWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final ActionErrandService actionErrandService) {
		super(processReportService, failureHandler);
		this.actionErrandService = actionErrandService;
	}

	// Why: an inspection without deficiencies or an assigned case worker is the case worker's to fix, so the process goes
	// back to the choice instead of raising an incident.
	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var errandId = getErrandId(externalTask);
		final var result = actionErrandService.createActionErrand(getMunicipalityId(externalTask), getNamespace(externalTask), errandId);
		final var actionErrandId = Optional.ofNullable(result.actionErrandId());

		final var report = ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_ACTION_ERRAND_CREATED, actionErrandId.isPresent()));

		return actionErrandId
			.map(id -> {
				logInfo("Errand {} has action errand {}", sanitizeForLogging(errandId), sanitizeForLogging(id));
				return report.withLogMessage("Action errand '%s' found or created".formatted(id));
			})
			.orElseGet(() -> {
				logInfo("{}", sanitizeForLogging(result.refusal()));
				return report.withRejection(result.refusal());
			});
	}
}
