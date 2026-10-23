package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;


import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_CHANGE;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/AlcoholServingChangeIT/", classes = Application.class)
class AlcoholServingChangeIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "7c1e4a9b-2d63-4f18-9a05-6b3e8d1f2a44";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		// Wait for the process to park in each phase, then signal that phase completed. The decision phase moves on by a decision event
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "investigation");
		completeDecision(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey());
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "follow_up");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "closure");

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
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Create change draft", "external_task_create_change_draft"),
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
				tuple("Set complete at", "external_task_set_complete_at"),
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
				tuple("Update asset", "external_task_update_asset"),
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

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The WAITING report is asserted by its mapping: the phase, its name and the signal the button carries
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Create change draft", "external_task_create_change_draft"));
	}

	@Test
	void test003_cancelledInRegistration() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		cancelProcess(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "registration");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The WAITING report is asserted by its mapping to offer the cancellation alongside the phase gate
		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Create change draft", "external_task_create_change_draft"),
				tuple("Registration completed", "await_registration_completed"));
	}

	@Test
	void test004_cancelledInInvestigation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "review");
		cancelProcess(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "investigation");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
				tuple("Start process", "start_process"),
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Create change draft", "external_task_create_change_draft"),
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Notify customer processing started", "external_task_notify_processing_started"),
				tuple("Review completed", "await_review_completed"),
				tuple("End review phase", "end_review_phase"),
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("Set complete at", "external_task_set_complete_at"),
				tuple("Investigation completed", "await_investigation_completed"));
	}

	@Test
	void test005_decisionRejectedBeforeThePhaseChangesNoAssetAndDoesNotWait() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_CHANGE.getKey(), "investigation");

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		verifyAllStubs();
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_update_asset");
	}

	@Test
	void test006_permitOfAnotherTypeLeavesAnIncident() throws JacksonException {
		// === Start process === the errand names a low-alcohol beer permit of the same holder
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_CHANGE.getKey());

		awaitIncidentAt(ALCOHOL_SERVING_CHANGE.getKey(), processInstanceId, "external_task_create_change_draft");

		verifyAllStubs();
		wiremock.verify(0, postRequestedFor(urlPathMatching("/api-support-management/.*/decisions")));
	}
}
