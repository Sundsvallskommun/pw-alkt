package apptest.verification;

import static org.assertj.core.api.Assertions.tuple;

public class ProcessPathway {

	public static Tuples registrationPathway() {
		return Tuples.create()
			.with(tuple("Registration", "registration_phase"))
			.with(tuple("Start registration phase", "start_registration_phase"))
			.with(tuple("Registration completed", "await_registration_completed"))
			.with(tuple("End registration phase", "end_registration_phase"));
	}

	public static Tuples reviewPathway() {
		return Tuples.create()
			.with(tuple("Review", "review_phase"))
			.with(tuple("Start review phase", "start_review_phase"))
			.with(tuple("Review completed", "await_review_completed"))
			.with(tuple("End review phase", "end_review_phase"));
	}

	public static Tuples investigationPathway() {
		return Tuples.create()
			.with(tuple("Investigation", "investigation_phase"))
			.with(tuple("Start investigation phase", "start_investigation_phase"))
			.with(tuple("Investigation completed", "await_investigation_completed"))
			.with(tuple("End investigation phase", "end_investigation_phase"));
	}

	public static Tuples decisionPathway() {
		return Tuples.create()
			.with(tuple("Decision", "decision_phase"))
			.with(tuple("Start decision phase", "start_decision_phase"))
			.with(tuple("Decision completed", "await_decision_completed"))
			.with(tuple("End decision phase", "end_decision_phase"));
	}

	public static Tuples followUpPathway() {
		return Tuples.create()
			.with(tuple("Follow up", "follow_up_phase"))
			.with(tuple("Start follow up phase", "start_follow_up_phase"))
			.with(tuple("Follow up completed", "await_follow_up_completed"))
			.with(tuple("End follow up phase", "end_follow_up_phase"));
	}

	public static Tuples closurePathway() {
		return Tuples.create()
			.with(tuple("Closure", "closure_phase"))
			.with(tuple("Start closure phase", "start_closure_phase"))
			.with(tuple("Closure completed", "await_closure_completed"))
			.with(tuple("End closure phase", "end_closure_phase"));
	}

	// The same phases in a model that holds no wait state in them: the process passes straight through, so the route
	// carries the phase, its start and its end, but no catch event. See the folkol models in processmodels/.

	public static Tuples registrationPassThroughPathway() {
		return Tuples.create()
			.with(tuple("Registration", "registration_phase"))
			.with(tuple("Start registration phase", "start_registration_phase"))
			.with(tuple("End registration phase", "end_registration_phase"));
	}

	public static Tuples reviewPassThroughPathway() {
		return Tuples.create()
			.with(tuple("Review", "review_phase"))
			.with(tuple("Start review phase", "start_review_phase"))
			.with(tuple("End review phase", "end_review_phase"));
	}

	public static Tuples investigationPassThroughPathway() {
		return Tuples.create()
			.with(tuple("Investigation", "investigation_phase"))
			.with(tuple("Start investigation phase", "start_investigation_phase"))
			.with(tuple("End investigation phase", "end_investigation_phase"));
	}

	public static Tuples decisionPassThroughPathway() {
		return Tuples.create()
			.with(tuple("Decision", "decision_phase"))
			.with(tuple("Start decision phase", "start_decision_phase"))
			.with(tuple("End decision phase", "end_decision_phase"));
	}
}
