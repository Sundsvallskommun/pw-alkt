package apptest;

import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION;

/**
 * What the reconciliation settles, and what it leaves alone. Every sweep is started by hand; max.retries=0 makes the
 * first failing work step an incident.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/ProcessReconciliationIT/", classes = Application.class)
@TestPropertySource(properties = {
	"reconciliation.worker.enabled=true",
	"camunda.worker.max.retries=0"
})
class ProcessReconciliationIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String TENANT_ID_ALKT = "ALKT";
	private static final String ERRAND_ID_INCIDENT = "a3690d5e-8f14-42b7-95ca-6d81e0b4f273";
	private static final String SCENARIO_INCIDENT = "incident-is-reported-once";
	private static final String PROCESSES_PATH_INCIDENT = "/api-support-management/2281/ALKT/errands/%s/processes".formatted(ERRAND_ID_INCIDENT);
	private static final String ERRAND_ID_VANISHED = "b7e4c210-58f3-4d96-a1c7-0e9d5f28a3b6";
	private static final String ERRAND_ID_ENDED_WHILE_DOWN = "e1c47b93-6a25-4f80-b3d6-9c052f7ae184";

	@Test
	void test001_incidentIsReportedOnce() throws JacksonException {
		// === Start process === every report it sends is refused, so the last work step leaves an incident behind
		final var processInstanceId = startProcess(ERRAND_ID_INCIDENT);

		completeEveryPhase(ERRAND_ID_INCIDENT, processInstanceId);

		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findIncidents(TENANT_ID_ALKT, PROCESS_KEY_TOBACCO_SALES).stream()
				.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId())));

		stubRowsOfTheErrand(processInstanceId);

		// The first sweep finds a row that still says RUNNING and reports the incident
		runReconciliation();

		// The second finds the row it just wrote, and has nothing to add. Its mappings allow one report and no more.
		runReconciliation();

		verify(exactly(2), getRequestedFor(urlPathEqualTo(PROCESSES_PATH_INCIDENT)));
		verifyAllStubs();
		verify(exactly(1), postRequestedFor(urlPathEqualTo("/api-messaging/2281/slack")));
	}

	@Test
	void test002_instanceThatVanishedIsSettled() throws JacksonException {
		// === Start process ===
		final var processInstanceId = startProcess(ERRAND_ID_VANISHED);

		// Someone cancels the instance from outside, and nothing reports it
		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		operatonClient.deleteProcessInstance(processInstanceId, false);

		runReconciliation();

		verifyAllStubs();
	}

	/** Support Management being down does not fail a step, so the instance ends and its row stays RUNNING until settled. */
	@Test
	void test003_instanceThatEndedWhileSupportManagementWasDownIsSettled() throws JacksonException {
		// === Start process === every report it sends on the way is answered with 500 and swallowed
		final var processInstanceId = startProcess(ERRAND_ID_ENDED_WHILE_DOWN);

		completeEveryPhase(ERRAND_ID_ENDED_WHILE_DOWN, processInstanceId);

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Support Management is back and never heard the process end
		runReconciliation();

		verifyAllStubs();
	}

	/**
	 * The two rows the sweeps read. A reconciliation only counts a row as its own when the row carries the process
	 * instance id, and the engine generates that at runtime, so a static mapping file cannot hold it. Registered after
	 * the last setupCall of the test case, which is what loads the mapping files.
	 */
	private static void stubRowsOfTheErrand(final String processInstanceId) {
		stubFor(get(urlPathEqualTo(PROCESSES_PATH_INCIDENT))
			.inScenario(SCENARIO_INCIDENT)
			.whenScenarioStateIs(STARTED)
			.willSetStateTo("first-sweep-is-running")
			.willReturn(okJson("""
				{"processes":[{"processInstanceId":"%s","processKey":"tobacco-sales","processStatus":"RUNNING"}]}""".formatted(processInstanceId))
				.withHeader("Content-Encoding", "identity")));

		stubFor(get(urlPathEqualTo(PROCESSES_PATH_INCIDENT))
			.inScenario(SCENARIO_INCIDENT)
			.whenScenarioStateIs("incident-has-been-reported")
			.willReturn(okJson("""
				{"processes":[{"processInstanceId":"%s","processKey":"tobacco-sales","processStatus":"FAILED","error":{"code":"INCIDENT"}}]}"""
				.formatted(processInstanceId))
				.withHeader("Content-Encoding", "identity")));
	}

	private String startProcess(final String errandId) throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		return awaitProcessInstance(errandId, PROCESS_KEY_TOBACCO_SALES);
	}

	private void completeEveryPhase(final String errandId, final String processInstanceId) {
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "registration");
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "review");
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "investigation");
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "decision");
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "follow_up");
		completePhase(errandId, processInstanceId, PROCESS_KEY_TOBACCO_SALES, "closure");
	}

	/** A sweep runs as its own process instance, so the test waits it out before looking at what it sent. */
	private void runReconciliation() {
		final var run = operatonClient.startProcessWithTenant(PROCESS_KEY_RECONCILIATION, TENANT_ID_ALKT, new StartProcessInstanceDto());

		awaitProcessCompleted(run.getId(), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
	}
}
