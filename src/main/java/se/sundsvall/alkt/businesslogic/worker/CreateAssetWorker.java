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
@ExternalTaskSubscription(topicName = "CreateAssetTask", lockDuration = CreateAssetWorker.LOCK_DURATION_IN_MILLISECONDS)
public class CreateAssetWorker extends AbstractTaskWorker {

	// Covers every call of a run timing out. A lock that expires mid-run lets another pod delete this run's draft.
	static final long LOCK_DURATION_IN_MILLISECONDS = 15 * 60 * 1000L;

	private final AssetService assetService;

	CreateAssetWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final AssetService assetService) {
		super(processReportService, failureHandler);
		this.assetService = assetService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var assetId = assetService.findOrCreateAsset(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask));

		logInfo("Errand {} has asset {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(assetId));

		return ProcessStateReport.running(externalTask.getActivityId(), null);
	}
}
