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
 * The last step of a process, sitting immediately before its end event. Support Management only learns that a process
 * is over through a report, and reports come from work steps.
 */
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
