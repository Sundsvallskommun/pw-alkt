package se.sundsvall.alkt.businesslogic.worker;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.InspectionProtocolService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PROTOCOL_FILE_NAME;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_PROTOCOL_TEMPLATE;
import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription(topicName = "CreateProtocolTask", lockDuration = CreateProtocolWorker.LOCK_DURATION_IN_MILLISECONDS)
public class CreateProtocolWorker extends AbstractTaskWorker {

	// Covers every call of a run timing out. A lock that expires mid-run lets another pod upload a second protocol.
	static final long LOCK_DURATION_IN_MILLISECONDS = 15 * 60 * 1000L;

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
			.withLogMessage("Protocol '%s' created".formatted(attachmentId));
	}
}
