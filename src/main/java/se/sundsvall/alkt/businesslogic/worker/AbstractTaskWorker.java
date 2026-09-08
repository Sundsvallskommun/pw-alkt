package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ProcessReportService;
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

	/** The state the process is in once the step is done. Returning it is how a step reports, so it cannot be skipped. */
	protected abstract ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService);

	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init(externalTask.getVariable(PROCESS_VARIABLE_REQUEST_ID));
		try {
			processReportService.reportProcessState(externalTask, ProcessStateReport.running(externalTask.getActivityId(), null));

			final var report = executeBusinessLogic(externalTask, externalTaskService);

			processReportService.reportProcessState(externalTask, report);
			externalTaskService.complete(externalTask, report.variables());
		} catch (final Exception e) {
			logException(externalTask, e);
			failureHandler.handleException(externalTaskService, externalTask, e.getMessage());
		} finally {
			RequestId.reset();
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
