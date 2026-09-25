package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;


import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/AlcoholServingIT/", classes = Application.class)
class AlcoholServingIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "3f7d5c21-9a44-4c6e-8b52-1e9f0a2d7c31";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		// Wait for the process to park in each phase, then signal that phase completed. The decision phase moves on by a decision event
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "registration");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "investigation");
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING);
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "closure");

		// Wait for process to finish
		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify mocked stubs
		verifyAllStubs();

		// Verify process pathway
		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// Registration
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Registration completed", "await_registration_completed"),
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
				tuple("Check decision", "external_task_check_decision"), // Approved
				tuple("Decision outcome", "gateway_decision_outcome"),
				tuple("Create asset", "external_task_create_asset"),
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
	void test002_stopsInRegistration() throws JacksonException {
		// === Start process ===
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The WAITING report is asserted by its mapping: the phase, its name and the signal the button carries
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Start registration phase", "start_registration_phase"));
	}

	@Test
	void test003_decisionRejectedBeforeThePhaseCreatesNoAssetAndDoesNotWait() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "registration");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "investigation");

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_create_asset");
	}

	@Test
	void test004_cancelledInRegistration() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		cancelProcess(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "registration");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The WAITING report is asserted by its mapping to offer the cancellation alongside the phase gate
		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Registration completed", "await_registration_completed"));
	}

	@Test
	void test005_cancelledInInvestigation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "registration");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "review");
		cancelProcess(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "investigation");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Review completed", "await_review_completed"),
				tuple("End review phase", "end_review_phase"),
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("Investigation completed", "await_investigation_completed"));
	}

	@Test
	void test006_cancelledWhileAwaitingTheDecision() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING);

		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "registration");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "review");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "investigation");

		// No decision yet, so the phase waits for one, and the cancellation is the only button it offers
		awaitProcessState(processInstanceId, "await_decision_updated", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("decision_phase");
		sendSignal(ERRAND_ID, PROCESS_KEY_ALCOHOL_SERVING, "process_cancelled");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Review completed", "await_review_completed"),
				tuple("End review phase", "end_review_phase"),
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("Investigation completed", "await_investigation_completed"),
				tuple("End investigation phase", "end_investigation_phase"),
				tuple("Decision", "decision_phase"),
				tuple("Start decision phase", "start_decision_phase"),
				tuple("Check decision", "external_task_check_decision"),
				tuple("Decision outcome", "gateway_decision_outcome"),
				tuple("Await decision", "gateway_await_decision"));
	}
}
