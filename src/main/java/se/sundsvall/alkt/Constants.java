package se.sundsvall.alkt;

import java.util.Set;

public final class Constants {

	// Each key must match the id of the process in its bpmn schema.
	public static final String PROCESS_KEY_ALCOHOL_SERVING = "alcohol-serving";
	public static final String PROCESS_KEY_ALCOHOL_SERVING_CHANGE = "alcohol-serving-change";
	public static final String PROCESS_KEY_ALCOHOL_SERVING_ADDITION = "alcohol-serving-addition";
	public static final String PROCESS_KEY_TOBACCO_SALES = "tobacco-sales";
	public static final String PROCESS_KEY_TOBACCO_SALES_CHANGE = "tobacco-sales-change";
	public static final String PROCESS_KEY_TOBACCO_SALES_CLOSURE = "tobacco-sales-closure";
	public static final String PROCESS_KEY_E_CIGARETTE_SALES = "e-cigarette-sales";
	public static final String PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING = "low-alcohol-beer-serving";
	public static final String PROCESS_KEY_LOW_ALCOHOL_BEER_SALES = "low-alcohol-beer-sales";
	public static final String PROCESS_KEY_EXTERNAL_INSPECTION = "external-inspection";
	public static final String PROCESS_KEY_INTERNAL_INSPECTION = "internal-inspection";

	// An errand event naming a key outside this set is answered with 422.
	public static final Set<String> PROCESS_KEYS = Set.of(
		PROCESS_KEY_ALCOHOL_SERVING,
		PROCESS_KEY_ALCOHOL_SERVING_CHANGE,
		PROCESS_KEY_ALCOHOL_SERVING_ADDITION,
		PROCESS_KEY_TOBACCO_SALES,
		PROCESS_KEY_TOBACCO_SALES_CHANGE,
		PROCESS_KEY_TOBACCO_SALES_CLOSURE,
		PROCESS_KEY_E_CIGARETTE_SALES,
		PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING,
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES,
		PROCESS_KEY_EXTERNAL_INSPECTION,
		PROCESS_KEY_INTERNAL_INSPECTION);

	// Outside PROCESS_KEYS on purpose: it belongs to no errand and must not be startable from an errand event.
	public static final String PROCESS_KEY_RECONCILIATION = "process-reconciliation";

	// Checked against the process consumer configured for the namespace; a report from anyone else is rejected.
	public static final String PROCESS_SERVICE = "pw-alkt";

	public static final String SENT_BY = PROCESS_SERVICE + "; type=processEngine";

	// Ours to choose, Support Management stores them without interpreting them.
	public static final String ERROR_CODE_RETRY = "RETRY";
	public static final String ERROR_CODE_INCIDENT = "INCIDENT";
	public static final String ERROR_CODE_TERMINATED = "TERMINATED";

	// Must match process-engine.deployment.processes[].tenant in application.yaml.
	public static final String TENANT_ID_ALKT = "ALKT";

	// Correlated for an event without a named signal: decision_updated for a decision, errandUpdated for anything else.
	// Neither is reported as a signal.
	public static final String MESSAGE_ERRAND_UPDATED = "errandUpdated";
	public static final String MESSAGE_DECISION_UPDATED = "decision_updated";

	public static final String DECISION_OUTCOME_NONE = "NONE";
	public static final String DECISION_OUTCOME_APPROVAL = "APPROVAL";
	public static final String DECISION_OUTCOME_REJECTION = "REJECTION";
	public static final String DECISION_STATUS_COMPLETED = "COMPLETED";
	public static final String STAKEHOLDER_ROLE_PERMIT_HOLDER = "APPLICANT";

	public static final String PROCESS_VARIABLE_DECISION_OUTCOME = "decisionOutcome";
	public static final String PROCESS_VARIABLE_ERRAND_ID = "errandId";
	public static final String PROCESS_VARIABLE_MUNICIPALITY_ID = "municipalityId";
	public static final String PROCESS_VARIABLE_NAMESPACE = "namespace";
	public static final String PROCESS_VARIABLE_REQUEST_ID = "requestId";

	private Constants() {}
}
