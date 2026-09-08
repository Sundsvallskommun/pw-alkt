package se.sundsvall.alkt;

import java.util.Set;

public final class Constants {

	/*
	 * One key per process definition in processmodels/ - each must match the ID of the process defined in its bpmn schema.
	 * They are all deployed to the same tenant by the resource pattern in application.yaml, so introducing a process is a
	 * schema next to the others plus a key here; no deployment configuration changes with it.
	 */
	public static final String PROCESS_KEY_ALCOHOL_SERVING = "alcohol-serving";
	public static final String PROCESS_KEY_ALCOHOL_SERVING_CHANGE = "alcohol-serving-change";
	public static final String PROCESS_KEY_ALCOHOL_SERVING_ADDITION = "alcohol-serving-addition";
	public static final String PROCESS_KEY_TOBACCO_SALES = "tobacco-sales";
	public static final String PROCESS_KEY_TOBACCO_SALES_CHANGE = "tobacco-sales-change";
	public static final String PROCESS_KEY_TOBACCO_SALES_CLOSURE = "tobacco-sales-closure";
	public static final String PROCESS_KEY_E_CIGARETTE_SALES = "e-cigarette-sales";
	public static final String PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING = "low-alcohol-beer-serving";
	public static final String PROCESS_KEY_LOW_ALCOHOL_BEER_SALES = "low-alcohol-beer-sales";
	public static final String PROCESS_KEY_SUPERVISION = "supervision";

	/**
	 * The keys above, as the set of process definitions this service has deployed. An errand event naming a key outside
	 * it is answered with 422, since no number of retries will make that process appear.
	 */
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
		PROCESS_KEY_SUPERVISION);

	// Namespace where the processes are deployed, a.k.a tenant. Must match process-engine.deployment.processes[].tenant
	// in application.yaml - the integration test covers the pairing.
	public static final String TENANT_ID_ALKT = "ALKT";

	// The errand this process instance drives, identified the way Support Management identifies it: a UUID string.
	public static final String PROCESS_VARIABLE_ERRAND_ID = "errandId";
	public static final String PROCESS_VARIABLE_MUNICIPALITY_ID = "municipalityId";
	public static final String PROCESS_VARIABLE_NAMESPACE = "namespace";
	public static final String PROCESS_VARIABLE_REQUEST_ID = "requestId";

	private Constants() {}
}
