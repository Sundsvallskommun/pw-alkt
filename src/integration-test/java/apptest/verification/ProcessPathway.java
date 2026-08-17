package apptest.verification;

import static org.assertj.core.api.Assertions.tuple;

/**
 * The activities each phase of the ansokan process is expected to pass through. Every phase is still an empty
 * subprocess, so a pathway only holds the subprocess itself and its start and end events; the activities of a phase are
 * added here as they are added to the schema.
 */
public class ProcessPathway {

	public static Tuples actualizationPathway() {
		return Tuples.create()
			.with(tuple("Actualization", "actualization_phase"))
			.with(tuple("Start actualization phase", "start_actualization_phase"))
			.with(tuple("End actualization phase", "end_actualization_phase"));
	}

	public static Tuples investigationPathway() {
		return Tuples.create()
			.with(tuple("Investigation", "investigation_phase"))
			.with(tuple("Start investigation phase", "start_investigation_phase"))
			.with(tuple("End investigation phase", "end_investigation_phase"));
	}

	public static Tuples decisionPathway() {
		return Tuples.create()
			.with(tuple("Decision", "decision_phase"))
			.with(tuple("Start decision phase", "start_decision_phase"))
			.with(tuple("End decision phase", "end_decision_phase"));
	}

	public static Tuples executionPathway() {
		return Tuples.create()
			.with(tuple("Execution", "execution_phase"))
			.with(tuple("Start execution phase", "start_execution_phase"))
			.with(tuple("End execution phase", "end_execution_phase"));
	}

	public static Tuples followUpPathway() {
		return Tuples.create()
			.with(tuple("Follow up", "follow_up_phase"))
			.with(tuple("Start follow up phase", "start_follow_up_phase"))
			.with(tuple("End follow up phase", "end_follow_up_phase"));
	}
}
