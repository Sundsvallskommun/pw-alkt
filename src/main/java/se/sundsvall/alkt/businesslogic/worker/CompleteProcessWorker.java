package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.api.model.ProcessStatus;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;

import static se.sundsvall.alkt.api.model.ProcessStatus.COMPLETED;

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
	protected ProcessStatus executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		logInfo("Process instance {} of errand {} reached its end", externalTask.getProcessInstanceId(), getErrandId(externalTask));

		return COMPLETED;
	}
}
