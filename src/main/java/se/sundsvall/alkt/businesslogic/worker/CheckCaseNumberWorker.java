package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.CaseNumberCheck;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("CheckCaseNumberTask")
public class CheckCaseNumberWorker extends AbstractTaskWorker {

	static final String PROCESS_VARIABLE_CASE_NUMBER_ACCEPTED = "caseNumberAccepted";

	private final ErrandService errandService;

	CheckCaseNumberWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final ErrandService errandService) {
		super(processReportService, failureHandler);
		this.errandService = errandService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var errandId = getErrandId(externalTask);
		final var check = errandService.checkCaseNumber(getMunicipalityId(externalTask), getNamespace(externalTask), errandId);

		logInfo("Case number of errand {}: {}", sanitizeForLogging(errandId), check);

		final var report = ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_CASE_NUMBER_ACCEPTED, check != CaseNumberCheck.MISSING));
		return switch (check) {
			// Why: Sundsvall has no case number to check, and an entry saying it was checked would mislead the case worker.
			case NOT_NEEDED -> report;
			case PRESENT -> report.withLogMessage("Case number checked");
			// Why: the case worker fills in the case number and moves the errand on again, so this is no incident.
			case MISSING -> report.withRejection("Errand '%s' in Timrå has no case number in timraCaseNumber".formatted(errandId));
		};
	}
}
