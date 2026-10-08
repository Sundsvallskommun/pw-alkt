package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
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

	// Input parameters of the step in the bpmn schema, not process variables.
	static final String PROCESS_VARIABLE_ACTION_ERRAND_CATEGORY = "actionErrandCategory";
	static final String PROCESS_VARIABLE_ACTION_ERRAND_TYPE = "actionErrandType";
	static final String PROCESS_VARIABLE_ACTION_ERRAND_CREATED = "actionErrandCreated";

	private final ActionErrandService actionErrandService;

	CreateActionErrandWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final ActionErrandService actionErrandService) {
		super(processReportService, failureHandler);
		this.actionErrandService = actionErrandService;
	}

	// Why: an inspection without deficiencies is the case worker's to fix, so the process goes back to the choice instead
	// of raising an incident.
	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var errandId = getErrandId(externalTask);
		final var actionErrandId = actionErrandService.createActionErrand(getMunicipalityId(externalTask), getNamespace(externalTask), errandId,
			externalTask.getVariable(PROCESS_VARIABLE_ACTION_ERRAND_CATEGORY), externalTask.getVariable(PROCESS_VARIABLE_ACTION_ERRAND_TYPE));

		final var report = ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_ACTION_ERRAND_CREATED, actionErrandId.isPresent()));

		return actionErrandId
			.map(id -> {
				logInfo("Errand {} has action errand {}", sanitizeForLogging(errandId), sanitizeForLogging(id));
				return report.withLogMessage("Action errand '%s' found or created".formatted(id));
			})
			.orElseGet(() -> {
				logInfo("Errand {} has no deficiencies, no action errand created", sanitizeForLogging(errandId));
				return report.withRejection("The inspection errand has no deficiencies, no action errand created");
			});
	}
}
