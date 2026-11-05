package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.DecisionBasisService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateDecisionBasisTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateDecisionBasisWorker extends AbstractTaskWorker {

	static final String INPUT_PARAMETER_DECISION_BASIS_TEMPLATE = "decisionBasisTemplate";

	private final DecisionBasisService decisionBasisService;

	CreateDecisionBasisWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final DecisionBasisService decisionBasisService) {
		super(processReportService, failureHandler);
		this.decisionBasisService = decisionBasisService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final String template = externalTask.getVariable(INPUT_PARAMETER_DECISION_BASIS_TEMPLATE);
		if (isBlank(template)) {
			throw new NonRetryableException("The step has no decision basis template, see the input parameters in the bpmn schema");
		}

		final var attachmentId = decisionBasisService.createDecisionBasis(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask), template);

		logInfo("Errand {} has decision basis {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(attachmentId));

		return running(externalTask)
			.withLogMessage("Decision basis '%s' found or created".formatted(attachmentId));
	}
}
