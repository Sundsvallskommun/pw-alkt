package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;

public abstract class AbstractTaskWorker implements ExternalTaskHandler {

	private final Logger logger;

	protected final FailureHandler failureHandler;

	protected AbstractTaskWorker(final FailureHandler failureHandler) {
		this.logger = LoggerFactory.getLogger(getClass());
		this.failureHandler = failureHandler;
	}

	protected void logInfo(final String msg, final Object... arguments) {
		logger.info(msg, arguments);
	}

	protected void logException(final ExternalTask externalTask, final Exception exception) {
		logger.error("Exception occurred in {} for task with id {} and businesskey {}", this.getClass().getSimpleName(), externalTask.getId(), externalTask.getBusinessKey(), exception);
	}

	protected abstract void executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService);

	@Override
	public void execute(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		RequestId.init(externalTask.getVariable(PROCESS_VARIABLE_REQUEST_ID));
		try {
			executeBusinessLogic(externalTask, externalTaskService);
		} finally {
			RequestId.reset();
		}
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
