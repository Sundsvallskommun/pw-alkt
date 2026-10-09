package se.sundsvall.alkt;

import java.util.Set;

public final class Constants {

	// Must match the process consumer configured for the namespace in Support Management, which rejects a report from
	// anyone else.
	public static final String PROCESS_SERVICE = "pw-alkt";
	public static final String SENT_BY = PROCESS_SERVICE + "; type=processEngine";

	// Must match process-engine.deployment.processes[].tenant in application.yaml.
	public static final String TENANT_ID_ALKT = "ALKT";

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
	public static final String PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING = "low-alcohol-beer-sales-and-serving";
	public static final String PROCESS_KEY_CATERING_OCCASION = "catering-occasion";
	public static final String PROCESS_KEY_EXTERNAL_INSPECTION = "external-inspection";
	public static final String PROCESS_KEY_INTERNAL_INSPECTION = "internal-inspection";
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
		PROCESS_KEY_LOW_ALCOHOL_BEER_SALES_AND_SERVING,
		PROCESS_KEY_CATERING_OCCASION,
		PROCESS_KEY_EXTERNAL_INSPECTION,
		PROCESS_KEY_INTERNAL_INSPECTION);
	// Outside PROCESS_KEYS on purpose: it belongs to no errand and must not be startable from an errand event.
	public static final String PROCESS_KEY_RECONCILIATION = "process-reconciliation";

	public static final String PROCESS_VARIABLE_ERRAND_ID = "errandId";
	public static final String PROCESS_VARIABLE_MUNICIPALITY_ID = "municipalityId";
	public static final String PROCESS_VARIABLE_NAMESPACE = "namespace";
	public static final String PROCESS_VARIABLE_REQUEST_ID = "requestId";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBER = "restaurantNumber";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT = "restaurantNumberLatestAssignment";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBER_ADDRESS_ID = "restaurantNumberAddressId";
	public static final String PROCESS_VARIABLE_CERTIFICATE_TEMPLATE = "certificateTemplate";

	public static final String MESSAGE_ERRAND_UPDATED = "errandUpdated";
	public static final String MESSAGE_DECISION_UPDATED = "decision_updated";
	public static final String MESSAGE_PROCESS_CANCELLED = "process_cancelled";

	public static final String ERROR_CODE_RETRY = "RETRY";
	public static final String ERROR_CODE_INCIDENT = "INCIDENT";
	public static final String ERROR_CODE_TERMINATED = "TERMINATED";

	public static final String LOG_TASK_GONE = "Task {} of process instance {} is gone (cancelled, deleted or completed elsewhere)";

	// Set by CheckDecisionTask while there is no completed decision; not an outcome Support Management knows.
	public static final String DECISION_OUTCOME_NONE = "NONE";
	// Must match the outcomes registered for the namespace in Support Management, and the gateway of every bpmn schema that
	// checks the decision.
	public static final String DECISION_OUTCOME_APPROVAL = "APPROVAL";
	public static final String DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS = "APPROVAL_WITH_CONDITIONS";
	public static final String DECISION_OUTCOME_REJECTED = "REJECTED";
	public static final String DECISION_OUTCOME_DISMISSED = "DISMISSED";
	public static final String DECISION_OUTCOME_INADMISSIBLE = "INADMISSIBLE";
	public static final Set<String> DECISION_OUTCOMES = Set.of(
		DECISION_OUTCOME_APPROVAL,
		DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS,
		DECISION_OUTCOME_REJECTED,
		DECISION_OUTCOME_DISMISSED,
		DECISION_OUTCOME_INADMISSIBLE);
	public static final Set<String> DECISION_OUTCOMES_CREATING_ASSET = Set.of(
		DECISION_OUTCOME_APPROVAL,
		DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS);
	public static final String DECISION_STATUS_COMPLETED = "COMPLETED";
	public static final String DECISION_STATUS_DRAFT = "DRAFT";
	public static final String DECISION_METHOD_AUTOMATIC = "AUTOMATIC";

	public static final String STAKEHOLDER_ROLE_PERMIT_HOLDER = "PRIMARY";
	public static final String NO_PERMIT_HOLDER_MESSAGE = "Errand '%s' has no stakeholder with role '" + STAKEHOLDER_ROLE_PERMIT_HOLDER + "'";

	public static final String PERMIT_PARAMETER_ERRAND_ID = "errandId";
	public static final String PERMIT_PARAMETER_LEGAL_BASIS = "legalBasis";
	public static final String PERMIT_PARAMETER_DELEGATION_REFERENCE = "delegationReference";
	public static final String PERMIT_PARAMETER_CONDITIONS = "conditions";
	// Named as the placeholder of the permit certificate, which is rendered from the parameters of the permit.
	public static final String PERMIT_PARAMETER_RESTAURANT_NUMBER = "premisesRestaurantNumber";

	public static final String ERRAND_PARAMETER_ASSET_ID = "assetId";
	public static final String ERRAND_PARAMETER_PREMISES_NAME = "premisesName";
	public static final String ERRAND_PARAMETER_PREMISES_STREET_ADDRESS = "premisesStreetAddress";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_CODE = "premisesPostalCode";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_AREA = "premisesPostalArea";
	// A number at the premises, or "true" in newRestaurantNumber for a new one. Without either the first free number is
	// used.
	public static final String ERRAND_PARAMETER_RESTAURANT_NUMBER = "restaurantNumber";
	public static final String ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER = "newRestaurantNumber";

	public static final String EXTERNAL_TAG_INSPECTION_ERRAND_ID = "inspectionErrandId";

	private Constants() {}
}
