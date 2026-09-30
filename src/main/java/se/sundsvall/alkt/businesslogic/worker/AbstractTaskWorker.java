package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.exception.NotFoundException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ProcessStatus;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static se.sundsvall.alkt.Constants.LOG_TASK_GONE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.service.model.ProcessStatus.RUNNING;
import static se.sundsvall.alkt.util.FailureDescription.describe;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

public abstract class AbstractTaskWorker implements ExternalTaskHandler {

	private final Logger logger;

	protected final ProcessReportService processReportService;
	protected final FailureHandler failureHandler;

	protected AbstractTaskWorker(final ProcessReportService processReportService, final FailureHandler failureHandler) {
		this.logger = LoggerFactory.getLogger(getClass());
		this.processReportService = processReportService;
		this.failureHandler = failureHandler;
	}

	/** A step that failed says so by throwing: a returned report always completes the task. */
	protected abstract ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService);

	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init(externalTask.getVariable(PROCESS_VARIABLE_REQUEST_ID));
		try {
			final ProcessStateReport report;
			try {
				reportProcessState(externalTask, RUNNING, () -> processReportService.reportStarted(externalTask));

				report = executeBusinessLogic(externalTask, externalTaskService);

				// Reported even when it is the same RUNNING again: Support Management holds the step as working until the task
				// reports a second time, and warns of concurrent tasks otherwise.
				reportProcessState(externalTask, report.status(), () -> processReportService.reportDone(externalTask, report));
				externalTaskService.complete(externalTask, report.variables());
			} catch (final NotFoundException e) {
				// The task is gone: the process was cancelled or deleted while the step ran, or another worker completed it after
				// the lock ran out. There is nothing to retry, and a result already reported stands.
				logInfo(LOG_TASK_GONE, sanitizeForLogging(externalTask.getId()),
					sanitizeForLogging(externalTask.getProcessInstanceId()));
				return;
			} catch (final NonRetryableException e) {
				logException(externalTask, e);
				handleFailure(externalTaskService, externalTask, describe(e), false);
				return;
			} catch (final Exception e) {
				logException(externalTask, e);
				handleFailure(externalTaskService, externalTask, describe(e), true);
				return;
			}

			// Outside the catch above on purpose: the task is completed by now, and handing a failure here to the failure
			// handler would retry a step that already did its work.
			if (!report.status().isTerminal()) {
				reportWaitState(externalTask);
			}
		} finally {
			RequestId.reset();
		}
	}

	protected void handleFailure(final ExternalTaskService externalTaskService, final ExternalTask externalTask, final String message, final boolean retryable) {
		if (retryable) {
			failureHandler.handleException(externalTaskService, externalTask, message);
		} else {
			failureHandler.handleIncident(externalTaskService, externalTask, message);
		}
	}

	// A failed report must not fail the business task. The one exception is a 412, which means the errand moved under us:
	// it propagates so the step reruns and rereads the errand.
	private void reportProcessState(final ExternalTask externalTask, final ProcessStatus status, final Runnable report) {
		try {
			report.run();
		} catch (final ClientProblem e) {
			if (PRECONDITION_FAILED.equals(e.getStatus())) {
				throw e;
			}
			logger.error("Could not report {} for task {}", status, sanitizeForLogging(externalTask.getId()), e);
		} catch (final Exception e) {
			logger.error("Could not report {} for task {}", status, sanitizeForLogging(externalTask.getId()), e);
		}
	}

	// Only after complete: the engine moves on when the task completes, so before that there is no subscription to read.
	protected void reportWaitState(final ExternalTask externalTask) {
		try {
			processReportService.reportWaitState(
				new ReportTarget(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask), externalTask.getProcessInstanceId(),
					externalTask.getProcessDefinitionKey(), null),
				externalTask.getProcessDefinitionId());
		} catch (final Exception e) {
			logger.error("Could not report the wait state after task {}", sanitizeForLogging(externalTask.getId()), e);
		}
	}

	protected void logInfo(final String msg, final Object... arguments) {
		logger.info(msg, arguments);
	}

	protected void logException(final ExternalTask externalTask, final Exception exception) {
		logger.error("Exception occurred in {} for task with id {} and business key {}", this.getClass().getSimpleName(), sanitizeForLogging(externalTask.getId()),
			sanitizeForLogging(externalTask.getBusinessKey()), exception);
	}

	protected String getMunicipalityId(final ExternalTask externalTask) {
		return externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID);
	}

	protected String getNamespace(final ExternalTask externalTask) {
		return externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE);
	}

	protected String getErrandId(final ExternalTask externalTask) {
		return externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID);
	}
}
