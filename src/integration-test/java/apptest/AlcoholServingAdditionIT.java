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
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_ADDITION;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/AlcoholServingAdditionIT/", classes = Application.class, sharedContext = false)
class AlcoholServingAdditionIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "0b93f26d-8c47-4e51-b27a-9d4c5e6f7081";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_ADDITION.getKey());

		// Wait for the process to park in each phase, then signal that phase completed. The decision phase moves on by a
		// decision event
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "investigation");
		completeDecision(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey());
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "follow_up");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "closure");

		// Wait for process to finish
		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify mocked stubs
		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "decision_phase", "follow_up_phase", "closure_phase");
		verifyAllStubs();
		// The addition reads the number of the permanent permit and must never write to licensed business
		wiremock.verify(0, postRequestedFor(urlPathMatching("/api-licensed-business/.*")));

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
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),

				// Review
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Notify customer processing started", "external_task_notify_processing_started"),
				tuple("Review completed", "await_review_completed"),
				tuple("Check case number", "external_task_check_case_number"),
				tuple("Case number accepted?", "gateway_case_number_accepted"),
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
				tuple("Find restaurant number", "external_task_find_restaurant_number"),
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

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_ADDITION.getKey());

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitWaitingReportAt("registration_phase");

		// The WAITING report is asserted by its mapping: the phase, its name and the signal the button carries
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"));
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

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_ADDITION.getKey());

		cancelProcess(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "registration");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The WAITING report is asserted by its mapping to offer the cancellation alongside the phase gate
		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
			tuple("Start process", "start_process"),
			tuple("Registration", "registration_phase"),
			tuple("Start registration phase", "start_registration_phase"),
			tuple("Create submission PDF", "external_task_create_submission_pdf"),
			tuple("No submission", "boundary_create_submission_pdf_skipped"),
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

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_ADDITION.getKey());

		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "review");
		cancelProcess(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "investigation");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase");
		verifyAllStubs();

		assertCancelledRoute(processInstanceId,
			tuple("Start process", "start_process"),
			tuple("Registration", "registration_phase"),
			tuple("Start registration phase", "start_registration_phase"),
			tuple("Create submission PDF", "external_task_create_submission_pdf"),
			tuple("No submission", "boundary_create_submission_pdf_skipped"),
			tuple("Registration completed", "await_registration_completed"),
			tuple("End registration phase", "end_registration_phase"),
			tuple("Review", "review_phase"),
			tuple("Start review phase", "start_review_phase"),
			tuple("Notify customer processing started", "external_task_notify_processing_started"),
			tuple("Review completed", "await_review_completed"),
			tuple("Check case number", "external_task_check_case_number"),
			tuple("Case number accepted?", "gateway_case_number_accepted"),
			tuple("End review phase", "end_review_phase"),
			tuple("Investigation", "investigation_phase"),
			tuple("Start investigation phase", "start_investigation_phase"),
			tuple("Set complete at", "external_task_set_complete_at"),
			tuple("Investigation completed", "await_investigation_completed"));
	}

	@Test
	void test005_decisionRejectedBeforeThePhaseCreatesNoAssetAndDoesNotWait() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING_ADDITION.getKey());

		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, ALCOHOL_SERVING_ADDITION.getKey(), "investigation");

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitWaitingReportAt("follow_up_phase");

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "follow_up_phase");
		verifyAllStubs();
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-licensed-business/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_create_asset");
	}
}
