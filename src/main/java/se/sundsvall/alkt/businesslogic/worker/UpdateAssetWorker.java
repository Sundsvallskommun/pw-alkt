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
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PERMIT_TYPE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "UpdateAssetTask", lockDuration = UpdateAssetWorker.LOCK_DURATION_IN_MILLISECONDS)
public class UpdateAssetWorker extends AbstractTaskWorker {

	// Covers every call of a run timing out, so another pod cannot pick the step up while this run still replaces the
	// certificate.
	static final long LOCK_DURATION_IN_MILLISECONDS = 15 * 60 * 1000L;

	private final AssetService assetService;

	UpdateAssetWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final AssetService assetService) {
		super(processReportService, failureHandler);
		this.assetService = assetService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var assetId = assetService.updateAsset(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getVariable(PROCESS_VARIABLE_CERTIFICATE_TEMPLATE), externalTask.getVariable(PROCESS_VARIABLE_PERMIT_TYPE));

		logInfo("Errand {} changed asset {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(assetId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Asset '%s' updated".formatted(assetId));
	}
}
