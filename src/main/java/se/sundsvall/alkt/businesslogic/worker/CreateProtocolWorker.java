package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.InspectionProtocolService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateProtocolTask", lockDuration = AbstractTaskWorker.LOCK_DURATION_COVERING_TIMEOUTS_IN_MILLISECONDS)
public class CreateProtocolWorker extends AbstractTaskWorker {

	// Input parameters of the step in the bpmn schema, not process variables.
	static final String PROCESS_VARIABLE_PROTOCOL_FILE_NAME = "protocolFileName";
	static final String PROCESS_VARIABLE_PROTOCOL_TEMPLATE = "protocolTemplate";

	private final InspectionProtocolService inspectionProtocolService;

	CreateProtocolWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final InspectionProtocolService inspectionProtocolService) {
		super(processReportService, failureHandler);
		this.inspectionProtocolService = inspectionProtocolService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var attachmentId = inspectionProtocolService.createProtocol(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask),
			externalTask.getVariable(PROCESS_VARIABLE_PROTOCOL_TEMPLATE), externalTask.getVariable(PROCESS_VARIABLE_PROTOCOL_FILE_NAME));

		logInfo("Errand {} has protocol {}", sanitizeForLogging(getErrandId(externalTask)), sanitizeForLogging(attachmentId));

		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withLogMessage("Protocol '%s' found or created".formatted(attachmentId));
	}
}
