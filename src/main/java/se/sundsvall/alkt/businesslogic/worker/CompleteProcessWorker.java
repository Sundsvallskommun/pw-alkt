package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("CompleteProcessTask")
public class CompleteProcessWorker extends AbstractTaskWorker {

	CompleteProcessWorker(final ProcessReportService processReportService, final FailureHandler failureHandler) {
		super(processReportService, failureHandler);
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		logInfo("Process instance {} of errand {} reached its end", sanitizeForLogging(externalTask.getProcessInstanceId()), sanitizeForLogging(getErrandId(externalTask)));

		return ProcessStateReport.completed();
	}
}
