package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/TobaccoSalesIT/", classes = Application.class, sharedContext = false)
class TobaccoSalesIT extends AbstractOperatonAppTest {

	private static final String ERRAND_ID = "5a2d7e13-4b98-4c26-8f71-3e0a9b6d5c82";

	@Test
	void test001_processWithoutDeviation() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		// The decision phase moves on by a decision event
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");
		completeDecision(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey());
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "follow_up");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "closure");

		awaitProcessCompleted(processInstanceId);

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "decision_phase", "follow_up_phase", "closure_phase");
		verifyAllStubs();

		// The decision basis is rendered and attached to the errand before the decision phase starts
		wiremock.verify(postRequestedFor(urlPathEqualTo("/api-templating/2262/render/pdf"))
			.withRequestBody(matchingJsonPath("$.identifier", equalTo("tobacco-sales-decision-basis"))));
		wiremock.verify(postRequestedFor(urlPathEqualTo("/api-support-management/2281/ALKT/errands/%s/attachments".formatted(ERRAND_ID)))
			.withAnyRequestBodyPart(aMultipart().withHeader("Content-Disposition", containing("filename=\"Beslutsunderlag TSN 2026-0153.pdf\""))));

		// Verify the activity log: the phases the process entered and the steps it took, named for the case worker
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'TASK' && @.activityId == 'external_task_create_decision_basis#done')].activityName",
				containing("Beslutsunderlaget har skapats och lagts till i ärendet"))));
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'PHASE' && @.activityId == 'registration_phase')].activityName",
				containing("Registrering har påbörjats"))));
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'TASK' && @.activityId == 'external_task_notify_processing_started#done')].activityName",
				containing("Kunden har fått besked om att handläggningen har börjat"))));
		wiremock.verify(putRequestedFor(urlPathMatching(".*/processes/[^/]+"))
			.withRequestBody(matchingJsonPath("$.activities[?(@.activityType == 'TASK' && @.activityId == 'external_task_create_asset#done' && @.severity == 'INFO')].message",
				containing("x-request-id"))));

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
				tuple("Create decision basis", "external_task_create_decision_basis"),
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
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		awaitProcessState(processInstanceId, "await_registration_completed");
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
	void test003_decisionRejectedBeforeThePhaseCreatesNoAssetAndDoesNotWait() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");

		awaitProcessState(processInstanceId, "await_follow_up_completed");
		awaitWaitingReportAt("follow_up_phase");

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "follow_up_phase");
		verifyAllStubs();
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_create_asset");
	}

	@Test
	void test004_cancelledInRegistration() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		cancelProcess(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");

		awaitProcessCompleted(processInstanceId);

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
	void test005_cancelledInInvestigation() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		cancelProcess(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");

		awaitProcessCompleted(processInstanceId);

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
	void test006_cancelledWhileAwaitingTheDecision() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");

		// No decision yet, so the phase waits for one, and the cancellation is the only button it offers
		awaitProcessState(processInstanceId, "await_decision_updated");
		awaitReportAt("decision_phase");
		sendSignal(ERRAND_ID, TOBACCO_SALES.getKey(), "process_cancelled");

		awaitProcessCompleted(processInstanceId);

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "decision_phase");
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
			tuple("Investigation completed", "await_investigation_completed"),
			tuple("Create decision basis", "external_task_create_decision_basis"),
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
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");
		completeDecision(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey());
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "follow_up");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "closure");

		awaitProcessCompleted(processInstanceId);

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "decision_phase", "follow_up_phase", "closure_phase");
		verifyAllStubs();

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "external_task_create_asset", "end_decision_phase", "end_process");

		// The certificate attached to the permit is kept on the errand as well
		wiremock.verify(postRequestedFor(urlPathEqualTo("/api-support-management/2281/ALKT/errands/%s/attachments".formatted(ERRAND_ID)))
			.withAnyRequestBodyPart(aMultipart()
				.withHeader("Content-Disposition", containing("filename=\"Tillståndsbevis ALKT-2026-000001.pdf\""))
				.withBody(equalTo("%PDF-1.7 tillstandsbevis"))));
	}

	/** A decision not to try the errand grants no permit, so the phase ends without one and without waiting. */
	@Test
	void test008_inadmissibleDecisionCreatesNoPermit() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, TOBACCO_SALES.getKey());

		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "registration");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "review");
		completePhase(ERRAND_ID, processInstanceId, TOBACCO_SALES.getKey(), "investigation");

		awaitProcessState(processInstanceId, "await_follow_up_completed");
		awaitWaitingReportAt("follow_up_phase");

		assertWaitingReportsInOrder("registration_phase", "review_phase", "investigation_phase", "follow_up_phase");
		verifyAllStubs();
		wiremock.verify(0, anyRequestedFor(urlPathMatching("/api-party-assets/.*")));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityId)
			.contains("external_task_check_decision", "gateway_decision_outcome", "end_decision_phase")
			.doesNotContain("gateway_await_decision", "await_decision_updated", "external_task_create_asset");
	}
}
