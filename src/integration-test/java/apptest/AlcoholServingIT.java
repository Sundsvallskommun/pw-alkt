package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;


import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;

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

		// Verify the activity log: the phases the process entered and the steps it took, named for the case worker
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'PHASE' && @.activityId == 'registration_phase')].activityName",
				containing("Registrering har påbörjats"))));
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'TASK' && @.activityId == 'external_task_notify_processing_started#done')].activityName",
				containing("Kunden har fått besked om att handläggningen har börjat"))));
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'TASK' && @.activityId == 'external_task_create_asset#done' && @.severity == 'INFO')].message",
				containing("x-request-id"))));

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
				tuple("Notify customer processing started", "external_task_notify_processing_started"),
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
				tuple("Resolve restaurant number", "external_task_resolve_restaurant_number"),
				tuple("Create asset", "external_task_create_asset"),
				tuple("Assign restaurant number", "external_task_assign_restaurant_number"),
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
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-licensed-business/.*")));

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
				tuple("Notify customer processing started", "external_task_notify_processing_started"),
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
				tuple("Notify customer processing started", "external_task_notify_processing_started"),
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

	/** An approval with conditions grants the permit as well, so the process takes the same path as an approval. */
	@Test
	void test007_approvalWithConditionsCreatesThePermit() throws JacksonException {
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
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING);
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "closure");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "external_task_create_asset", "end_decision_phase", "end_process");
	}

	/** A decision not to try the errand grants no permit, so the phase ends without one and without waiting. */
	@Test
	void test008_inadmissibleDecisionCreatesNoPermit() throws JacksonException {
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
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-licensed-business/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_create_asset");
	}

	/** An owner change: the case worker picks the number the premises already has, and it is assigned to the new holder. */
	@Test
	void test009_chosenRestaurantNumberOfAnOwnerChangeIsAssigned() throws JacksonException {
		runThroughAnApproval();

		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-licensed-business/2281/restaurant-numbers/available")));
		wiremock.verify(0, postRequestedFor(urlPathMatching("/api-licensed-business/2281/restaurant-numbers")));
	}

	/** A new number is created at the premises although one there is free, since the case worker asked for a new one. */
	@Test
	void test010_newRestaurantNumberIsCreatedAndAssigned() throws JacksonException {
		runThroughAnApproval();

		wiremock.verify(1, postRequestedFor(urlPathMatching("/api-licensed-business/2281/restaurant-numbers")));
	}

	/**
	 * The number was free when it was chosen, but another errand assigned it before this one could, so the process stops
	 * instead of ending the other holder's assignment.
	 */
	@Test
	void test011_anotherErrandAssignedTheNumberFirstLeavesAnIncident() throws JacksonException {
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
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING);

		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findIncidents(TENANT_ID_ALKT, PROCESS_KEY_ALCOHOL_SERVING).stream()
				.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId())
					&& "external_task_assign_restaurant_number".equals(incident.getActivityId())));
		// The failure report and the alert are sent after the incident is raised, the alert last
		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.untilAsserted(() -> wiremock.verify(postRequestedFor(urlPathEqualTo("/api-messaging/2281/slack"))));

		verifyAllStubs();
		wiremock.verify(0, postRequestedFor(urlPathMatching("/api-licensed-business/2281/assignments")));
	}

	private void runThroughAnApproval() throws JacksonException {
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
		completeDecision(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING);
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_ALCOHOL_SERVING, "closure");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		// The route is ordered by end time, and the phase can end in the same millisecond as its last step, so only the steps
		// are held to their order.
		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.containsSubsequence("external_task_resolve_restaurant_number", "external_task_create_asset", "external_task_assign_restaurant_number")
			.contains("end_decision_phase");
	}
}
