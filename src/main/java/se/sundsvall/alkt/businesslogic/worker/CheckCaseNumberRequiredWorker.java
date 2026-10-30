package se.sundsvall.alkt.businesslogic.worker;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.service.ErrandService;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.alkt.service.model.ProcessStateReport;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

@Component
@ExternalTaskSubscription("CheckCaseNumberRequiredTask")
public class CheckCaseNumberRequiredWorker extends AbstractTaskWorker {

	static final String PROCESS_VARIABLE_CASE_NUMBER_REQUIRED = "caseNumberRequired";

	private final ErrandService errandService;

	CheckCaseNumberRequiredWorker(final ProcessReportService processReportService, final FailureHandler failureHandler, final ErrandService errandService) {
		super(processReportService, failureHandler);
		this.errandService = errandService;
	}

	@Override
	protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
		final var caseNumberRequired = errandService.isCaseNumberRequired(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask));

		logInfo("Errand {} requires a case number: {}", sanitizeForLogging(getErrandId(externalTask)), caseNumberRequired);

		// Why: the step only reads, so it leaves no entry in the activity log.
		return ProcessStateReport.running(externalTask.getActivityId(), null)
			.withVariables(Map.of(PROCESS_VARIABLE_CASE_NUMBER_REQUIRED, caseNumberRequired));
	}
}
