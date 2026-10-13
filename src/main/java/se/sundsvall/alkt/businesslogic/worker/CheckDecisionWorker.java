package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.DecisionService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("CheckDecisionTask")
public class CheckDecisionWorker extends AbstractTaskWorker {

	static final String PROCESS_VARIABLE_DECISION_OUTCOME = "decisionOutcome";

	private final DecisionService decisionService;

	CheckDecisionWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final DecisionService decisionService) {
		super(processReportService, failureHandler);
		this.decisionService = decisionService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var processKey = externalTask.getProcessDefinitionKey();
		final var outcome = decisionService.getDecisionOutcome(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask));

		logInfo("Decision of errand {} has outcome {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(outcome));

		// Why: the case worker can correct the decision, and the next decision event or the hourly check then finds it. An
		// incident would hold the process on this step, where neither reaches it. The outcome is the key of the refusal, so
		// the hourly check does not log the same refusal again.
		if (!decisionService.isKnownOutcome(processKey, outcome)) {
			return ProcessStateReport.running(externalTask.getActivityId(), null)
				.withVariables(Map.of(PROCESS_VARIABLE_DECISION_OUTCOME, DECISION_OUTCOME_NONE))
				.withRejection("Decision outcome %s is not an outcome of process %s".formatted(outcome, processKey), outcome);
		}

		final var report = ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_DECISION_OUTCOME, outcome));

		// Why: without a decision the step reruns every hour, and a check that found nothing is not worth an entry each time.
		if (DECISION_OUTCOME_NONE.equals(outcome)) {
			return report;
		}
		return report.withLogMessage("Decision outcome %s".formatted(outcome));
	}
}
