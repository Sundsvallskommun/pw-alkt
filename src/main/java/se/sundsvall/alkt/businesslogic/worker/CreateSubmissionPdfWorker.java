package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.StepSkippedException;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.SubmissionPdfService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateSubmissionPdfTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateSubmissionPdfWorker extends AbstractTaskWorker {

	private final SubmissionPdfService submissionPdfService;

	CreateSubmissionPdfWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final SubmissionPdfService submissionPdfService) {
		super(processReportService, failureHandler);
		this.submissionPdfService = submissionPdfService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var errandId = getErrandId(externalTask);
		final var logMessage = switch (submissionPdfService.createSubmissionPdf(getMunicipalityId(externalTask), getNamespace(externalTask), errandId)) {
			case CREATED -> "Submission PDF created and attached to the errand";
			case ALREADY_EXISTS -> "Submission PDF already on the errand, not created again";
			case SKIPPED -> throw new StepSkippedException("Errand '%s' has no JSON parameters, so there is no submission to make a PDF of".formatted(errandId));
		};

		logInfo("Errand {}: {}", sanitizeForLogging(errandId), logMessage);
		return ProcessStateReport.running(externalTask.getActivityId(), null).withLogMessage(logMessage);
	}
}
