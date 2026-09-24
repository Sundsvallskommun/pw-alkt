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
@ExternalTaskSubscription(topicName = "CreateDecisionTask", lockDuration = CreateDecisionWorker.LOCK_DURATION_IN_MILLISECONDS)
public class CreateDecisionWorker extends AbstractTaskWorker {

	// Covers every call of a run timing out. A lock that expires mid-run lets another pod write a second decision.
	static final long LOCK_DURATION_IN_MILLISECONDS = 15 * 60 * 1000L;

	private final DecisionService decisionService;

	CreateDecisionWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final DecisionService decisionService) {
		super(processReportService, failureHandler);
		this.decisionService = decisionService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var decisionId = decisionService.createDecision(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getProcessDefinitionKey());

		logInfo("Errand {} has decision {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(decisionId));

		return ProcessStateReport.running(externalTask.getActivityId(), null);
	}
}
