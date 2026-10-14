package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
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

		// Why: Support Management locks a completed decision, so the case worker cannot correct it and only an incident tells
		// anyone.
		if (!decisionService.isKnownOutcome(processKey, outcome)) {
			throw new NonRetryableException("Decision outcome %s is not an outcome of process %s".formatted(outcome, processKey));
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
