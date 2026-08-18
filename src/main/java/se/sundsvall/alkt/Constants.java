package se.sundsvall.alkt;

import generated.se.sundsvall.operaton.VariableValueDto;
import org.camunda.bpm.engine.variable.type.ValueType;

public final class Constants {

	/*
	 * One key per process definition in processmodels/ - each must match the ID of the process defined in its bpmn schema.
	 * Additional processes (anmalan, tillsyn) are added here as their schemas are introduced; they are deployed to the
	 * same tenant by the resource pattern in application.yaml, so no deployment configuration changes with them.
	 */
	public static final String PROCESS_KEY_ANSOKAN = "alkt-ansokan";

	// Namespace where the processes are deployed, a.k.a tenant. Must match process-engine.deployment.processes[].tenant
	// in application.yaml - the integration test covers the pairing.
	public static final String TENANT_ID_ALKT = "ALKT";

	// The errand this process instance drives, identified the way Support Management identifies it: a UUID string.
	public static final String PROCESS_VARIABLE_ERRAND_ID = "errandId";
	public static final String PROCESS_VARIABLE_MUNICIPALITY_ID = "municipalityId";
	public static final String PROCESS_VARIABLE_NAMESPACE = "namespace";
	public static final String PROCESS_VARIABLE_REQUEST_ID = "requestId";
	public static final String PROCESS_VARIABLE_UPDATE_AVAILABLE = "updateAvailable";

	public static final VariableValueDto FALSE = new VariableValueDto().type(ValueType.BOOLEAN.getName()).value(false);

	private Constants() {}
}
