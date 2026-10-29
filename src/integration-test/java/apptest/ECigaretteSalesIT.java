package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.service.model.ProcessType.E_CIGARETTE_SALES;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/ECigaretteSalesIT/", classes = Application.class, sharedContext = false)
class ECigaretteSalesIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "6d19b3e8-7a24-4f60-9c83-5b0e2d4a8f15";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, E_CIGARETTE_SALES.getKey());

		// A case worker created the errand, so it waits in the registration
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "registration");

		// The premises are in Timrå, so the review waits for the case worker. The errand has no case number from the records
		// of Timrå yet, so the check warns and the review waits again
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "review");
		await().until(() -> countReports("$.activities[?(@.activityId =~ /external_task_check_case_number#rejected#.*/)]") == 1);
		awaitReportsAt("review_phase", 2);
		awaitProcessState(processInstanceId, "await_review_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The case worker fills in the case number and completes the review again
		wiremock.setScenarioState("timra-case-number", "case-number-filled");
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "review");

		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "follow_up");
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "closure");

		// Wait for process to finish
		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify mocked stubs
		verifyAllStubs();
		assertWaitingReportsInOrder("registration_phase", "review_phase", "follow_up_phase", "closure_phase");

		// The process never writes to the errand itself, so its errand number and case number stay as the case worker left them
		wiremock.verify(0, patchRequestedFor(urlPathMatching("/api-support-management/2281/ALKT/errands/%s(/parameters)?".formatted(ERRAND_ID))));

		// Verify process pathway
		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// Registration - not from the e-service, so the phase waits for the case worker
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),

				// Review - in Timrå it waits for the case worker, twice, since the first check found no case number
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Check premises municipality", "external_task_check_premises_municipality"),
				tuple("Premises municipality", "gateway_premises_municipality"),
				tuple("Review completed", "await_review_completed"),
				tuple("Check case number", "external_task_check_case_number"),
				tuple("Case number accepted?", "gateway_case_number_accepted"),
				tuple("Review completed", "await_review_completed"),
				tuple("Check case number", "external_task_check_case_number"),
				tuple("Case number accepted?", "gateway_case_number_accepted"),
				tuple("End review phase", "end_review_phase"),

				// Investigation - no catch event
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("End investigation phase", "end_investigation_phase"),

				// Decision - no catch event: the decision is made and the permit created without a case worker
				tuple("Decision", "decision_phase"),
				tuple("Start decision phase", "start_decision_phase"),
				tuple("Create decision", "external_task_create_decision"),
				tuple("Create asset", "external_task_create_asset"),
				tuple("End decision phase", "end_decision_phase"),

				// Follow up - no gate between the review and here
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
	void test002_approvesTheNotificationAndCreatesThePermitBeforeFollowUp() throws JacksonException {
		// === Start process ===
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, E_CIGARETTE_SALES.getKey());

		// A case worker created the errand, so it waits in the registration
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "registration");

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitWaitingReportAt("follow_up_phase");

		// The mappings assert every call: the decision written as a draft, linked to the attachment and completed, the permit
		// created from it and activated, and the WAITING report of follow up with the signal its button carries
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// No case worker after the registration: review and investigation pass straight through, the decision phase runs its
				// two steps
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", "external_task_create_submission_pdf"),
				tuple("No submission", "boundary_create_submission_pdf_skipped"),
				tuple("Registration completed", "await_registration_completed"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
				tuple("Check premises municipality", "external_task_check_premises_municipality"),
				tuple("Premises municipality", "gateway_premises_municipality"),
				tuple("End review phase", "end_review_phase"),
				tuple("Investigation", "investigation_phase"),
				tuple("Start investigation phase", "start_investigation_phase"),
				tuple("End investigation phase", "end_investigation_phase"),
				tuple("Decision", "decision_phase"),
				tuple("Start decision phase", "start_decision_phase"),
				tuple("Create decision", "external_task_create_decision"),
				tuple("Create asset", "external_task_create_asset"),
				tuple("End decision phase", "end_decision_phase"),

				// Parked here: the follow up phase has not ended, so neither it nor its catch event is in the route yet
				tuple("Start follow up phase", "start_follow_up_phase"));
	}

	@Test
	void test003_cancelledInFollowUp() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, E_CIGARETTE_SALES.getKey());

		// A case worker created the errand, so it waits in the registration
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "registration");

		cancelProcess(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "follow_up");

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The mappings assert the cancellation offered on the RUNNING report of the decision and next to the gate of follow up,
		// and the process reported COMPLETED once it is used. The decision and the permit made before it stay as they are.
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
			tuple("Check premises municipality", "external_task_check_premises_municipality"),
			tuple("Premises municipality", "gateway_premises_municipality"),
			tuple("End review phase", "end_review_phase"),
			tuple("Investigation", "investigation_phase"),
			tuple("Start investigation phase", "start_investigation_phase"),
			tuple("End investigation phase", "end_investigation_phase"),
			tuple("Decision", "decision_phase"),
			tuple("Start decision phase", "start_decision_phase"),
			tuple("Create decision", "external_task_create_decision"),
			tuple("Create asset", "external_task_create_asset"),
			tuple("End decision phase", "end_decision_phase"),
			tuple("Follow up", "follow_up_phase"),
			tuple("Start follow up phase", "start_follow_up_phase"),
			tuple("Follow up completed", "await_follow_up_completed"));
	}

	@Test
	void test004_errandWithoutPermitHolderLeavesAnIncidentBeforeTheDecision() throws JacksonException {
		// === Start process === the errand has no stakeholder with role PRIMARY
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, E_CIGARETTE_SALES.getKey());

		// A case worker created the errand, so it waits in the registration
		completePhase(ERRAND_ID, processInstanceId, E_CIGARETTE_SALES.getKey(), "registration");

		awaitIncidentAt(E_CIGARETTE_SALES.getKey(), processInstanceId, "external_task_create_decision");

		// No decision is written, so nothing is locked before the case worker has added the permit holder
		verifyAllStubs();
		wiremock.verify(0, postRequestedFor(urlPathMatching("/api-support-management/.*/decisions")));
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));
	}
}
