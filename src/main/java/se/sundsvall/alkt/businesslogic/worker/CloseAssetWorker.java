package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.AssetService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
// A second run is harmless: If-Match turns it into a retry that finds the permit closed.
@ExternalTaskSubscription(topicName = "CloseAssetTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CloseAssetWorker extends AbstractTaskWorker {

	private final AssetService assetService;

	CloseAssetWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final AssetService assetService) {
		super(processReportService, failureHandler);
		this.assetService = assetService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var assetId = assetService.closeAsset(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			getProcessType(externalTask).getAssetType());

		logInfo("Errand {} ended asset {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(assetId));

		return running(externalTask)
			.withLogMessage("Asset '%s' closed".formatted(assetId));
	}
}
