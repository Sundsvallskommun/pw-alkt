package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.DecisionService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateDecisionTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateDecisionWorker extends AbstractTaskWorker {

	// An input parameter of the step in the bpmn schema, not a process variable: the errand parameter with the last day of
	// the decision.
	static final String PROCESS_VARIABLE_VALID_TO_PARAMETER = "validToParameter";

	private final DecisionService decisionService;

	CreateDecisionWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final DecisionService decisionService) {
		super(processReportService, failureHandler);
		this.decisionService = decisionService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var decisionId = decisionService.approveAutomatically(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getProcessDefinitionKey(), externalTask.getVariable(PROCESS_VARIABLE_VALID_TO_PARAMETER));

		logInfo("Errand {} has decision {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(decisionId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Decision '%s' completed".formatted(decisionId));
	}
}
