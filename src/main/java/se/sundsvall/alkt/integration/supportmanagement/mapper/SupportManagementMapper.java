package se.sundsvall.alkt.integration.supportmanagement.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandProcessReport;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.ProcessSignal;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.client.task.ExternalTask;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.alkt.service.model.AwaitingSignal;
import se.sundsvall.alkt.service.model.ProcessStateReport;
import se.sundsvall.alkt.service.model.ReportTarget;
import se.sundsvall.dept44.support.Relation;
import se.sundsvall.dept44.support.Relation.ResourceIdentifier;

import static java.util.Collections.emptyList;
import static java.util.Comparator.nullsLast;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static se.sundsvall.alkt.Constants.DECISION_METHOD_AUTOMATIC;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_COMPLETED;
import static se.sundsvall.alkt.Constants.DECISION_STATUS_DRAFT;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETERS_OUTSIDE_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_SERVICE;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_ERRAND_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_MUNICIPALITY_ID;
import static se.sundsvall.alkt.Constants.PROCESS_VARIABLE_NAMESPACE;
import static se.sundsvall.alkt.Constants.STAKEHOLDER_ROLE_PERMIT_HOLDER;

public final class SupportManagementMapper {

	static final String DECISION_TYPE_PERMIT = "PERMIT";
	static final String ERRAND_RESOURCE_TYPE = "case";
	static final String ERRAND_SERVICE = "supportmanagement";

	private static final Map<String, String> DECISION_TITLES = Map.of(
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES, "Anmälan om försäljning av folköl",
		PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING, "Anmälan om servering av folköl",
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING, "Anmälan om försäljning och servering av folköl");

	private static final Map<String, String> CHANGE_DRAFT_TITLES = Map.of(
		PROCESS_KEY_ALCOHOL_SERVING_CHANGE, "Ändring av serveringstillstånd",
		PROCESS_KEY_TOBACCO_SALES_CHANGE, "Ändring av tobakstillstånd");

	private SupportManagementMapper() {}

	/** A relation from the errand; the target is left to the service that creates it. */
	public static String toErrandRelation(final String relationType, final String errandId, final String namespace) {
		return Relation.create(relationType, ResourceIdentifier.create(errandId, ERRAND_RESOURCE_TYPE, ERRAND_SERVICE, namespace), null)
			.toRelationString();
	}

	/** A permit holder is the primary stakeholder, known by its external id. */
	public static boolean isPermitHolder(final Stakeholder stakeholder) {
		return STAKEHOLDER_ROLE_PERMIT_HOLDER.equals(stakeholder.getRole()) && nonNull(stakeholder.getExternalId());
	}

	public static Optional<String> toPartyId(final Errand errand) {
		return Optional.ofNullable(errand.getStakeholders()).orElse(emptyList()).stream()
			.filter(SupportManagementMapper::isPermitHolder)
			.map(Stakeholder::getExternalId)
			.findFirst();
	}

	public static String toNoPermitHolderMessage(final String errandId) {
		return "Errand '%s' has no stakeholder with role '%s'".formatted(errandId, STAKEHOLDER_ROLE_PERMIT_HOLDER);
	}

	public static String toDecisionTitle(final String processKey) {
		return Optional.ofNullable(processKey)
			.map(DECISION_TITLES::get)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no title for a decision made automatically, one of %s was expected"
				.formatted(processKey, DECISION_TITLES.keySet())));
	}

	public static String toChangeDraftTitle(final String processKey) {
		return Optional.ofNullable(processKey)
			.map(CHANGE_DRAFT_TITLES::get)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no title for a change draft, one of %s was expected"
				.formatted(processKey, CHANGE_DRAFT_TITLES.keySet())));
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

	// Why: Support Management lets the process write only an automatic decision, with outcome and decidedAt already on the
	// draft; the case worker's edit makes it manual. A parameter without a value asks for a removal, so it is kept.
	public static Decision toChangeDraft(final Errand errand, final String title, final OffsetDateTime decidedAt) {
		return new Decision()
			.type(DECISION_TYPE_PERMIT)
			.status(DECISION_STATUS_DRAFT)
			.method(DECISION_METHOD_AUTOMATIC)
			.decidedBy(PROCESS_SERVICE)
			.decidedAt(decidedAt)
			.outcome(DECISION_OUTCOME_APPROVAL)
			.title(title)
			.description(errand.getTitle())
			.parameters(Optional.ofNullable(errand.getParameters()).orElse(emptyList()).stream()
				.filter(parameter -> !ERRAND_PARAMETERS_OUTSIDE_CHANGE.contains(parameter.getKey()))
				.toList());
	}

	/** The keys of the parameters of the decision that have no value, which a change removes from the permit. */
	public static Set<String> toRemovedParameterKeys(final Decision decision) {
		return Optional.ofNullable(decision.getParameters()).orElse(emptyList()).stream()
			.filter(parameter -> Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream().allMatch(StringUtils::isBlank))
			.map(Parameter::getKey)
			.collect(toSet());
	}

	// Why: the generated model starts parameters as an empty list, and an empty list removes the decision's parameters.
	public static Decision toDecisionCompletion(final OffsetDateTime decidedAt) {
		return new Decision()
			.status(DECISION_STATUS_COMPLETED)
			.decidedAt(decidedAt)
			.completedAt(decidedAt)
			.parameters(null);
	}

	/** Each parameter by its key, its values joined. A parameter without a value is left out. */
	public static Map<String, String> toParameterValues(final List<Parameter> parameters) {
		final var parameterValues = new LinkedHashMap<String, String>();
		Optional.ofNullable(parameters).orElse(emptyList())
			.forEach(parameter -> {
				final var value = Optional.ofNullable(parameter.getValues()).orElse(emptyList()).stream()
					.filter(StringUtils::isNotBlank)
					.collect(joining(", "));
				if (isNotBlank(value)) {
					parameterValues.put(parameter.getKey(), value);
				}
			});
		return parameterValues;
	}

	/** The terms of the decision are its conditions, one per line in their order, and empty for a decision without any. */
	public static String toConditions(final Decision decision) {
		return Optional.ofNullable(decision.getTerms()).orElse(emptyList()).stream()
			.sorted(Comparator.comparing(DecisionTerm::getSortOrder, nullsLast(Comparator.naturalOrder())))
			.map(DecisionTerm::getText)
			.filter(StringUtils::isNotBlank)
			.collect(joining("\n"));
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
	public static ErrandProcessReport toErrandProcessReport(final ReportTarget target, final ProcessStateReport report) {
		return new ErrandProcessReport()
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
