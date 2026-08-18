package se.sundsvall.alkt.businesslogic.worker;

import generated.se.sundsvall.operaton.VariableValueDto;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.dept44.requestid.RequestId;

import static se.sundsvall.alkt.Constants.FALSE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_REQUEST_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_UPDATE_AVAILABLE;

/**
 * Base class for the external task workers of this service. It owns the engine-facing plumbing - request id handling,
 * process variable access and failure reporting - so that a worker implementation only has to provide the business
 * logic of its task.
 */
public abstract class AbstractTaskWorker implements ExternalTaskHandler {

	private final Logger logger;

	private final OperatonClient operatonClient;
	protected final FailureHandler failureHandler;

	protected AbstractTaskWorker(final OperatonClient operatonClient, final FailureHandler failureHandler) {
		this.logger = LoggerFactory.getLogger(getClass());
		this.operatonClient = operatonClient;
		this.failureHandler = failureHandler;
	}

	protected void clearUpdateAvailable(final ExternalTask externalTask) {
		/*
		 * Clearing process variable has to be a blocking operation.
		 * Using ExternalTaskService.setVariables() will not work without creating race conditions.
		 */
		operatonClient.setProcessInstanceVariable(externalTask.getProcessInstanceId(), PROCESS_VARIABLE_UPDATE_AVAILABLE, FALSE);
	}

	protected void setProcessInstanceVariable(final ExternalTask externalTask, final String variableName, final VariableValueDto variableValue) {
		operatonClient.setProcessInstanceVariable(externalTask.getProcessInstanceId(), variableName, variableValue);
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
		/*
		 * RequestId.init() only writes to the MDC when the thread local counter is zero and increments it afterwards.
		 * Without a matching reset() the counter never returns to zero, which would make every task after the first one on
		 * a given worker thread log under the request id of that first task.
		 */
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
