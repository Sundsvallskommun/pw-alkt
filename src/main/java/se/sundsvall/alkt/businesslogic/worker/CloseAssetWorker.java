package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.AssetService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PERMIT_TYPE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CloseAssetTask", lockDuration = CloseAssetWorker.LOCK_DURATION_IN_MILLISECONDS)
public class CloseAssetWorker extends AbstractTaskWorker {

	// The same lock as the other steps that write to party-assets. A second run is harmless: If-Match turns it into a retry
	// that finds the permit closed.
	static final long LOCK_DURATION_IN_MILLISECONDS = 15 * 60 * 1000L;

	private final AssetService assetService;

	CloseAssetWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final AssetService assetService) {
		super(processReportService, failureHandler);
		this.assetService = assetService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var assetId = assetService.closeAsset(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getVariable(PROCESS_VARIABLE_PERMIT_TYPE));

		logInfo("Errand {} ended asset {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(assetId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Asset '%s' closed".formatted(assetId));
	}
}
