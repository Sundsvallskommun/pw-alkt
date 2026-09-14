package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.ErrandProcess;
import org.camunda.bpm.client.task.ExternalTask;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

public final class SupportManagementMapper {

	private SupportManagementMapper() {}

	public static ReportTarget toReportTarget(final ExternalTask externalTask) {
		return new ReportTarget(
			externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID),
			externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE),
			externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID),
			externalTask.getProcessInstanceId(),
			externalTask.getProcessDefinitionKey(),
			externalTask.getId());
	}

	/** The instance id is left out of the body: Support Management takes it from the path and rejects a different one. */
	public static ErrandProcess toErrandProcess(final ReportTarget target, final ProcessStateReport report) {
		return new ErrandProcess()
			.processService(PROCESS_SERVICE)
			.processKey(target.processKey())
			.processStatus(report.status().name())
			.currentActivityId(report.currentActivityId())
			.currentActivityName(report.currentActivityName())
			.externalTaskId(target.externalTaskId())
			.error(report.error())
			.activities(report.activities());
	}
}
