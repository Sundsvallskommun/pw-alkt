package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ProcessSignal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;

import static se.sundsvall.alkt.Constants.DECISION_METHOD_AUTOMATIC;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_DRAFT;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;

public final class SupportManagementMapper {

	static final String DECISION_TYPE_PERMIT = "PERMIT";

	private static final Map<String, String> DECISION_TITLES = Map.of(
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES, "Tillstånd för försäljning av folköl",
		PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING, "Tillstånd för servering av folköl");

	private SupportManagementMapper() {}

	public static String toDecisionTitle(final String processKey) {
		return Optional.ofNullable(processKey)
			.map(DECISION_TITLES::get)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no title for a decision made automatically, one of %s was expected"
				.formatted(processKey, DECISION_TITLES.keySet())));
	}

	// Why: an approval of a notification holds until further notice, so it has no last day. Support Management requires
	// decidedAt already on the draft.
	public static Decision toAutomaticDecision(final String title, final Errand errand, final LocalDate validFrom, final OffsetDateTime decidedAt) {
		return new Decision()
			.type(DECISION_TYPE_PERMIT)
			.status(DECISION_STATUS_DRAFT)
			.method(DECISION_METHOD_AUTOMATIC)
			.decidedBy(PROCESS_SERVICE)
			.decidedAt(decidedAt)
			.outcome(DECISION_OUTCOME_APPROVAL)
			.title(title)
			.description(errand.getTitle())
			.validFrom(validFrom);
	}

	public static Decision toDecisionCompletion(final OffsetDateTime decidedAt) {
		return new Decision()
			.status(DECISION_STATUS_COMPLETED)
			.decidedAt(decidedAt)
			.completedAt(decidedAt);
	}

	public static ReportTarget toReportTarget(final ExternalTask externalTask) {
		return new ReportTarget(
			externalTask.getVariable(PROCESS_VARIABLE_MUNICIPALITY_ID),
			externalTask.getVariable(PROCESS_VARIABLE_NAMESPACE),
			externalTask.getVariable(PROCESS_VARIABLE_ERRAND_ID),
			externalTask.getProcessInstanceId(),
			externalTask.getProcessDefinitionKey(),
			externalTask.getId());
	}

	/**
	 * The instance id is left out of the body: Support Management takes it from the path and rejects a different one.
	 * errandVersion stands in for an If-Match for a step that only read the errand; null skips the version check.
	 */
	public static ErrandProcess toErrandProcess(final ReportTarget target, final ProcessStateReport report) {
		return new ErrandProcess()
			.processService(PROCESS_SERVICE)
			.processKey(target.processKey())
			.processStatus(report.status().name())
			.currentActivityId(report.currentActivityId())
			.currentActivityName(report.currentActivityName())
			.externalTaskId(target.externalTaskId())
			.errandVersion(report.errandVersion())
			.error(report.error())
			.activities(report.activities())
			.awaitingSignals(toProcessSignals(report.awaitingSignals()));
	}

	private static List<ProcessSignal> toProcessSignals(final List<AwaitingSignal> awaitingSignals) {
		return awaitingSignals.stream()
			.map(signal -> new ProcessSignal()
				.name(signal.name())
				.label(signal.label()))
			.toList();
	}
}
