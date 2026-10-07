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

	public static final String PROCESS_VARIABLE_ACTION_ERRAND_CREATED = "actionErrandCreated";
	public static final String PROCESS_VARIABLE_DECISION_OUTCOME = "decisionOutcome";
	public static final String PROCESS_VARIABLE_ERRAND_ID = "errandId";
	public static final String PROCESS_VARIABLE_MUNICIPALITY_ID = "municipalityId";
	public static final String PROCESS_VARIABLE_NAMESPACE = "namespace";
	public static final String PROCESS_VARIABLE_REQUEST_ID = "requestId";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBER = "restaurantNumber";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBER_LATEST_ASSIGNMENT = "restaurantNumberLatestAssignment";
	public static final String PROCESS_VARIABLE_RESTAURANT_NUMBERS_BEFORE_CREATE = "restaurantNumbersBeforeCreate";
	// Input parameters of a step in the bpmn schema, not process variables.
	public static final String PROCESS_VARIABLE_ACTION_ERRAND_CATEGORY = "actionErrandCategory";
	public static final String PROCESS_VARIABLE_ACTION_ERRAND_TYPE = "actionErrandType";
	public static final String PROCESS_VARIABLE_CERTIFICATE_TEMPLATE = "certificateTemplate";
	public static final String PROCESS_VARIABLE_MESSAGE = "message";
	public static final String PROCESS_VARIABLE_PROTOCOL_FILE_NAME = "protocolFileName";
	public static final String PROCESS_VARIABLE_PROTOCOL_TEMPLATE = "protocolTemplate";

	public static final String MESSAGE_ERRAND_UPDATED = "errandUpdated";
	public static final String MESSAGE_DECISION_UPDATED = "decision_updated";
	public static final String MESSAGE_PROCESS_CANCELLED = "process_cancelled";

	// Must match the errorCode of the bpmn:error that the boundary event of a skippable step catches.
	public static final String BPMN_ERROR_STEP_SKIPPED = "step_skipped";

	// Must match the id of the cancellation step in every bpmn schema.
	public static final String ACTIVITY_CANCEL_PROCESS = "external_task_cancel_process";

	public static final String ERROR_CODE_RETRY = "RETRY";
	public static final String ERROR_CODE_INCIDENT = "INCIDENT";
	public static final String ERROR_CODE_TERMINATED = "TERMINATED";
	public static final String ERROR_CODE_SKIPPED = "SKIPPED";
	public static final String ERROR_CODE_REJECTED = "REJECTED";

	public static final String ACTIVITY_TYPE_TASK = "TASK";
	public static final String ACTIVITY_TYPE_PHASE = "PHASE";
	public static final String ACTIVITY_TYPE_INCIDENT = "INCIDENT";
	public static final String ACTIVITY_TYPE_RECONCILIATION = "RECONCILIATION";

	// Support Management refuses a severity it does not know.
	public static final String SEVERITY_INFO = "INFO";
	public static final String SEVERITY_WARN = "WARN";
	public static final String SEVERITY_ERROR = "ERROR";

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

	public static final String PERMIT_TYPE_ALCOHOL_SERVING = "AlcoholServingPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES = "LowAlcoholBeerSalesPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SERVING = "LowAlcoholBeerServingPermit";
	public static final String PERMIT_TYPE_LOW_ALCOHOL_BEER_SALES_AND_SERVING = "LowAlcoholBeerSalesAndServingPermit";

	public static final String PERMIT_PARAMETER_ERRAND_ID = "errandId";
	public static final String PERMIT_PARAMETER_LEGAL_BASIS = "legalBasis";
	public static final String PERMIT_PARAMETER_DELEGATION_REFERENCE = "delegationReference";
	public static final String PERMIT_PARAMETER_CONDITIONS = "conditions";
	// Named as the placeholder of the permit certificate, which is rendered from the parameters of the permit.
	public static final String PERMIT_PARAMETER_RESTAURANT_NUMBER = "premisesRestaurantNumber";
	// Set by pw-alkt from the errand and the decision, so a decision cannot remove them.
	public static final Set<String> PERMIT_PARAMETERS_OF_THE_PROCESS = Set.of(PERMIT_PARAMETER_ERRAND_ID, PERMIT_PARAMETER_LEGAL_BASIS,
		PERMIT_PARAMETER_DELEGATION_REFERENCE, PERMIT_PARAMETER_CONDITIONS, PERMIT_PARAMETER_RESTAURANT_NUMBER);

	public static final String ERRAND_PARAMETER_ASSET_ID = "assetId";
	public static final String ERRAND_PARAMETER_PREMISES_NAME = "premisesName";
	public static final String ERRAND_PARAMETER_PREMISES_STREET_ADDRESS = "premisesStreetAddress";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_CODE = "premisesPostalCode";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_AREA = "premisesPostalArea";
	// A number at the premises, or "true" in newRestaurantNumber for a new one. Without either the first free number is
	// used.
	public static final String ERRAND_PARAMETER_RESTAURANT_NUMBER = "restaurantNumber";
	public static final String ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER = "newRestaurantNumber";
	// The parameters of a change errand that are not part of the change the customer asks for. Every other one is.
	public static final Set<String> ERRAND_PARAMETERS_OUTSIDE_CHANGE = Set.of(ERRAND_PARAMETER_ASSET_ID, PERMIT_PARAMETER_ERRAND_ID, PERMIT_PARAMETER_LEGAL_BASIS,
		PERMIT_PARAMETER_DELEGATION_REFERENCE, PERMIT_PARAMETER_CONDITIONS, ERRAND_PARAMETER_PREMISES_NAME, ERRAND_PARAMETER_PREMISES_STREET_ADDRESS,
		ERRAND_PARAMETER_PREMISES_POSTAL_CODE, ERRAND_PARAMETER_PREMISES_POSTAL_AREA, ERRAND_PARAMETER_RESTAURANT_NUMBER, ERRAND_PARAMETER_NEW_RESTAURANT_NUMBER,
		PERMIT_PARAMETER_RESTAURANT_NUMBER);

	// Must match the topic Mina sidor gives the external conversation it creates.
	public static final String CONVERSATION_TOPIC_CUSTOMER = "Mina Sidor";
	public static final String EXTERNAL_TAG_INSPECTION_ERRAND_ID = "inspectionErrandId";
	public static final String RELATION_TYPE_LINK = "LINK";
	public static final String MEASURE_STATUS_ACTIVE = "ACTIVE";
	public static final String INVESTIGATION_STATUS_COMPLETED = "COMPLETED";

	private Constants() {}
}
