package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * The last step of a cancelled process. The cancellation can arrive in any phase, e.g. when the applicant withdraws the
 * errand.
 */
@Component
@ExternalTaskSubscription("CancelProcessTask")
public class CancelProcessWorker extends AbstractTaskWorker {

	CancelProcessWorker(final ProcessReportService processReportService, final FailureHandler failureHandler) {
		super(processReportService, failureHandler);
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		logInfo("Process instance {} of errand {} was cancelled and is reported as completed", sanitizeForLogging(externalTask.getProcessInstanceId()),
			sanitizeForLogging(getErrandId(externalTask)));

		return ProcessStateReport.completed();
	}
}
