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
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/LowAlcoholBeerServingIT/", classes = Application.class)
class LowAlcoholBeerServingIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "c30a6f18-9d54-4b72-8e05-2f1a7c4b6d98";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING);

		// The first four phases hold no catch event in this model, so follow up is the first one to park
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING, "follow_up");
		completePhase(ERRAND_ID, processInstanceId, PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING, "closure");

		// Wait for process to finish
		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify mocked stubs
		verifyAllStubs();

		// Verify process pathway
		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// Registration - no catch event, so the phase walks straight through
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("End registration phase", "end_registration_phase"),

				// Review - no catch event
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
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

				// Follow up - the first phase this model parks in
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

		final var processInstanceId = awaitProcessInstance(ERRAND_ID, PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING);

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The mappings assert every call: the decision written as a draft, linked to the attachment and completed, the permit
		// created from it and activated, and the WAITING report of follow up with the signal its button carries
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),

				// No case worker before follow up: three phases pass straight through, the decision phase runs its two steps
				tuple("Registration", "registration_phase"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("End registration phase", "end_registration_phase"),
				tuple("Review", "review_phase"),
				tuple("Start review phase", "start_review_phase"),
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
}
