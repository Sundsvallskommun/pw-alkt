package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.DecisionService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitType;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateChangeDraftTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateChangeDraftWorker extends AbstractTaskWorker {

	private final DecisionService decisionService;

	CreateChangeDraftWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final DecisionService decisionService) {
		super(processReportService, failureHandler);
		this.decisionService = decisionService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var decisionId = decisionService.createChangeDraft(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			toPermitType(externalTask.getProcessDefinitionKey()), externalTask.getProcessDefinitionKey());

		logInfo("Errand {} has decision draft {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(decisionId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Errand has decision '%s'".formatted(decisionId));
	}
}
