package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ACTION_ERRAND;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/ActionErrandIT/", classes = Application.class)
class ActionErrandIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "9d1c3b5a-7e2f-4a6c-8b0d-1e3f5a7c9b2d";

	@Test
	void test001_processWithWarning() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ACTION_ERRAND);

		// The registration passes straight through. The decision phase moves on by a decision event
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "investigation");
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND);
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "closure");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// Registration
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("End registration phase", "end_registration_phase"),

				// Review
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Review completed", "await_review_completed"),
				tuple("End review phase", "end_review_phase"),

				// Investigation
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("Investigation completed", "await_investigation_completed"),
				tuple("End investigation phase", "end_investigation_phase"),

				// Decision
				tuple("Decision", "decision_phase"),
				tuple("Start decision phase", "start_decision_phase"),
				tuple("Check decision", "external_task_check_decision"), // No decision yet
				tuple("Decision outcome", "gateway_decision_outcome"),
				tuple("Await decision", "gateway_await_decision"),
				tuple("Decision updated", "await_decision_updated"),
				tuple("Check decision", "external_task_check_decision"), // Warning
				tuple("Decision outcome", "gateway_decision_outcome"),
				tuple("End decision phase", "end_decision_phase"),

				// Follow up
				tuple("Follow up", "follow_up_phase"),
				tuple("Start follow up phase", "start_follow_up_phase"),
				tuple("Follow up completed", "await_follow_up_completed"),
				tuple("End follow up phase", "end_follow_up_phase"),

				// Closure
				tuple("Closure", "closure_phase"),
				tuple("Start closure phase", "start_closure_phase"),
				tuple("Closure completed", "await_closure_completed"),
				tuple("End closure phase", "end_closure_phase"),

				// At end of process
				tuple("Complete process", "external_task_complete_process"), // Reports COMPLETED to Support Management
				tuple("End process", "end_process"));
	}

	@Test
	void test002_noDecisionNeeded() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ACTION_ERRAND);

		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "investigation");
		awaitProcessState(processInstanceId, "await_decision_not_needed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("decision_phase");
		sendSignal(ERRAND_ID, PROCESS_KEY_ACTION_ERRAND, "decision_not_needed");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "closure");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Review completed", "await_review_completed"),
				tuple("End review phase", "end_review_phase"),
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("Investigation completed", "await_investigation_completed"),
				tuple("End investigation phase", "end_investigation_phase"),

				// Decision, ended by the button without a decision
				tuple("Decision", "decision_phase"),
				tuple("Start decision phase", "start_decision_phase"),
				tuple("Check decision", "external_task_check_decision"),
				tuple("Decision outcome", "gateway_decision_outcome"),
				tuple("Await decision", "gateway_await_decision"),
				tuple("No decision needed", "await_decision_not_needed"),
				tuple("End decision phase", "end_decision_phase"),

				tuple("Follow up", "follow_up_phase"),
				tuple("Start follow up phase", "start_follow_up_phase"),
				tuple("Follow up completed", "await_follow_up_completed"),
				tuple("End follow up phase", "end_follow_up_phase"),
				tuple("Closure", "closure_phase"),
				tuple("Start closure phase", "start_closure_phase"),
				tuple("Closure completed", "await_closure_completed"),
				tuple("End closure phase", "end_closure_phase"),
				tuple("Complete process", "external_task_complete_process"),
				tuple("End process", "end_process"));
	}

	@Test
	void test003_cancelledInReview() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ACTION_ERRAND);

		cancelProcess(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "review");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
			tuple("Start process", "start_process"),
			tuple("Registration", "registration_phase"),
			tuple("Start registration phase", "start_registration_phase"),
			tuple("End registration phase", "end_registration_phase"),
			tuple("Start review phase", "start_review_phase"),
			tuple("Review completed", "await_review_completed"),
			tuple("Review", "review_phase"));
	}

	@Test
	void test004_outcomeOfAnotherProcessLeavesAnIncident() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ACTION_ERRAND);

		// The decision has the outcome of a permit. Support Management locks a completed decision, so it cannot be corrected
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND, "investigation");
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ACTION_ERRAND);

		awaitIncidentAt(PROCESS_KEY_ACTION_ERRAND, processInstanceId, "external_task_check_decision");

		verifyAllStubs();
	}
}
