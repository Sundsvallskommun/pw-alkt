package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.AssetService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_CERTIFICATE_TEMPLATE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_RESTAURANT_NUMBER;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitType;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateAssetTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateAssetWorker extends AbstractTaskWorker {

	private final AssetService assetService;

	CreateAssetWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final AssetService assetService) {
		super(processReportService, failureHandler);
		this.assetService = assetService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var assetId = assetService.findOrCreateAsset(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getVariable(PROCESS_VARIABLE_CERTIFICATE_TEMPLATE), toPermitType(externalTask.getProcessDefinitionKey()),
			externalTask.getVariable(PROCESS_VARIABLE_RESTAURANT_NUMBER));

		logInfo("Errand {} has asset {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(assetId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Asset '%s' found or created".formatted(assetId));
	}
}
