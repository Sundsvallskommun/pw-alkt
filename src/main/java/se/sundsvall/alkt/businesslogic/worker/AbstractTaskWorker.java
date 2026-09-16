package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
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

	/**
	 * The state the process is in once the step is done. Returning it is how a step reports, so it cannot be skipped. A
	 * step that failed says so by throwing - a returned report always completes the task, and only COMPLETED and FAILED
	 * survive it, since anything else is overwritten by the wait state the engine moved on to.
	 */
	protected abstract ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService);

	/**
	 * RUNNING goes out before the step, what the step returned after it. A report Support Management refuses does not
	 * fail the step, except a 412 (see reportProcessState); a final report that never arrived is what the reconciliation
	 * settles later.
	 */
	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init(externalTask.getVariable(PROCESS_VARIABLE_REQUEST_ID));
		try {
			final ProcessStateReport report;
			try {
				reportProcessState(externalTask, ProcessStateReport.running(externalTask.getActivityId(), null));

				report = executeBusinessLogic(externalTask, externalTaskService);

				reportProcessState(externalTask, report);
				externalTaskService.complete(externalTask, report.variables());
			} catch (final Exception e) {
				logException(externalTask, e);
				failureHandler.handleException(externalTaskService, externalTask, e.getMessage());
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

	// A failed report must not fail the business task - same rule FailureHandler.reportFailure applies on the failure
	// path. Exception: a 412 means the errand moved under us, so it is left to propagate - the step reruns and its
	// second attempt rereads the errand. dept44's Feign error decoder collapses every upstream error into
	// ClientProblem(BAD_GATEWAY, ...), so there is no typed status to match on here; the original status only
	// survives in the message text.
	private void reportProcessState(final ExternalTask externalTask, final ProcessStateReport report) {
		try {
			processReportService.report(externalTask, report);
		} catch (final ClientProblem e) {
			if (isPreconditionFailed(e)) {
				throw e;
			}
			logger.error("Could not report {} for task {}", report.status(), sanitizeForLogging(externalTask.getId()), e);
		} catch (final Exception e) {
			logger.error("Could not report {} for task {}", report.status(), sanitizeForLogging(externalTask.getId()), e);
		}
	}

	/**
	 * Only after complete: the engine moves on when the task completes, so before that there is no subscription to read.
	 */
	private void reportWaitState(final ExternalTask externalTask) {
		try {
			processReportService.reportWaitState(
				new ReportTarget(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask), externalTask.getProcessInstanceId(),
					externalTask.getProcessDefinitionKey(), null),
				externalTask.getProcessDefinitionId());
		} catch (final Exception e) {
			logger.error("Could not report the wait state after task {}", sanitizeForLogging(externalTask.getId()), e);
		}
	}

	private static boolean isPreconditionFailed(final ClientProblem e) {
		return (e.getMessage() != null) && e.getMessage().contains("412");
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
