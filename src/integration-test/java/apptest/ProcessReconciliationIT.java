package apptest;

import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION_NIGHTLY;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.service.model.ProcessType.CATERING_OCCASION;
import static se.sundsvall.alkt.service.model.ProcessType.EXTERNAL_INSPECTION;
import static se.sundsvall.alkt.service.model.ProcessType.LOW_ALCOHOL_BEER_SALES;

/**
 * What the reconciliation settles, and what it leaves alone. Every sweep is started by hand; max.retries=0 makes the
 * first failing work step an incident.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/ProcessReconciliationIT/", classes = Application.class, sharedContext = false)
@TestPropertySource(properties = {
	"reconciliation.worker.enabled=true",
	"camunda.worker.max.retries=0"
})
class ProcessReconciliationIT extends AbstractOperatonAppTest {

	private static final String ERRAND_ID_INCIDENT = "a3690d5e-8f14-42b7-95ca-6d81e0b4f273";
	private static final String SCENARIO_INCIDENT = "incident-is-reported-once";
	private static final String PROCESSES_PATH_INCIDENT = "/api-support-management/2281/ALKT/errands/%s/processes".formatted(ERRAND_ID_INCIDENT);
	private static final String ERRAND_ID_NIGHTLY_INCIDENT = "d4b81f6e-2c93-4a07-8e5d-73a0c9f1b2e8";
	private static final String SCENARIO_NIGHTLY_INCIDENT = "nightly-incident-is-reported-once";
	private static final String PROCESSES_PATH_NIGHTLY_INCIDENT = "/api-support-management/2281/ALKT/errands/%s/processes".formatted(ERRAND_ID_NIGHTLY_INCIDENT);
	private static final String ERRAND_ID_VANISHED = "b7e4c210-58f3-4d96-a1c7-0e9d5f28a3b6";
	private static final String ERRAND_ID_ENDED_WHILE_DOWN = "e1c47b93-6a25-4f80-b3d6-9c052f7ae184";

	@Value("${integration.operaton.url}")
	private String operatonUrl;

	/**
	 * The timers fire on the clock, and a sweep of their own while a test runs reads the rows once more than the test
	 * counts. Suspended for the shared engine, then what they may already have started is removed.
	 */
	@BeforeEach
	void suspendTheTimers() {
		List.of(PROCESS_KEY_RECONCILIATION, PROCESS_KEY_RECONCILIATION_NIGHTLY).forEach(processKey -> {
			RestClient.create().put()
				.uri(operatonUrl + "/job-definition/suspended")
				.contentType(APPLICATION_JSON)
				.body("""
					{"processDefinitionKey":"%s","processDefinitionTenantId":"%s","suspended":true,"includeJobs":true}""".formatted(processKey, TENANT_ID_ALKT))
				.retrieve()
				.toBodilessEntity();

			operatonClient.findProcessInstances(null, processKey, TENANT_ID_ALKT)
				.forEach(instance -> operatonClient.deleteProcessInstance(instance.getId(), false));
		});
	}

	@Test
	void test001_incidentIsReportedOnce() throws JacksonException {
		// Every report it sends is refused, so the first work step leaves an incident behind
		final var processInstanceId = startProcess(ERRAND_ID_INCIDENT, LOW_ALCOHOL_BEER_SALES.getKey());

		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findIncidents(TENANT_ID_ALKT, LOW_ALCOHOL_BEER_SALES.getKey(), ANY_INCIDENT_TIME).stream()
				.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId())));

		stubRowsOfTheErrand(PROCESSES_PATH_INCIDENT, SCENARIO_INCIDENT, processInstanceId);
		// The work step read the row as well, to tell whether it entered a phase
		final var readsByTheProcess = wiremock.findAll(getRequestedFor(urlPathEqualTo(PROCESSES_PATH_INCIDENT))).size();

		// The first sweep finds a row that still says RUNNING and reports the incident
		runReconciliation(PROCESS_KEY_RECONCILIATION);

		// The second finds the row it just wrote, and has nothing to add. Its mappings allow one report and no more.
		runReconciliation(PROCESS_KEY_RECONCILIATION);

		verify(exactly(readsByTheProcess + 2), getRequestedFor(urlPathEqualTo(PROCESSES_PATH_INCIDENT)));
		// The step left its own entry: the time in the id, the attempt and the request id in the message
		verify(putRequestedFor(urlPathMatching(PROCESSES_PATH_INCIDENT + "/[^/]+"))
			.withRequestBody(matchingJsonPath("$.processStatus", equalTo("FAILED")))
			.withRequestBody(matchingJsonPath("$.activities[0].activityType", equalTo("TASK")))
			.withRequestBody(matchingJsonPath("$.activities[0].activityId", matching("external_task_[a-z_]+#[0-9]+")))
			.withRequestBody(matchingJsonPath("$.activities[0].errorCode", equalTo("INCIDENT")))
			.withRequestBody(matchingJsonPath("$.activities[0].message", containing("Attempt 1 of 1, x-request-id "))));
		verifyAllStubs();
		verify(exactly(1), postRequestedFor(urlPathEqualTo("/api-messaging/2281/slack")));
	}

	@Test
	void test002_instanceThatVanishedIsSettled() throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID_VANISHED, EXTERNAL_INSPECTION.getKey());

		// Someone cancels the instance from outside, and nothing reports it
		awaitProcessState(processInstanceId, "await_registration_completed");
		operatonClient.deleteProcessInstance(processInstanceId, false);

		runReconciliation(PROCESS_KEY_RECONCILIATION);

		verifyAllStubs();
	}

	/** Support Management being down does not fail a step, so the instance ends and its row stays RUNNING until settled. */
	@Test
	void test003_instanceThatEndedWhileSupportManagementWasDownIsSettled() throws JacksonException {
		// Every report it sends on the way is answered with 500 and swallowed
		final var processInstanceId = startProcess(ERRAND_ID_ENDED_WHILE_DOWN, CATERING_OCCASION.getKey());

		completePhase(ERRAND_ID_ENDED_WHILE_DOWN, processInstanceId, CATERING_OCCASION.getKey(), "registration");
		completePhase(ERRAND_ID_ENDED_WHILE_DOWN, processInstanceId, CATERING_OCCASION.getKey(), "follow_up");
		completePhase(ERRAND_ID_ENDED_WHILE_DOWN, processInstanceId, CATERING_OCCASION.getKey(), "closure");

		awaitProcessCompleted(processInstanceId);

		// Support Management is back and never heard the process end
		wiremock.setScenarioState("support-management-comes-back", "support-management-is-back");
		runReconciliation(PROCESS_KEY_RECONCILIATION);

		verifyAllStubs();
	}

	@Test
	void test004_incidentIsReportedOnceByTheNightlySweep() throws JacksonException {
		// Every report it sends is refused, so the first work step leaves an incident behind
		final var processInstanceId = startProcess(ERRAND_ID_NIGHTLY_INCIDENT, LOW_ALCOHOL_BEER_SALES.getKey());

		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findIncidents(TENANT_ID_ALKT, LOW_ALCOHOL_BEER_SALES.getKey(), ANY_INCIDENT_TIME).stream()
				.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId())));

		stubRowsOfTheErrand(PROCESSES_PATH_NIGHTLY_INCIDENT, SCENARIO_NIGHTLY_INCIDENT, processInstanceId);
		final var readsByTheProcess = wiremock.findAll(getRequestedFor(urlPathEqualTo(PROCESSES_PATH_NIGHTLY_INCIDENT))).size();

		// The first nightly sweep reports the incident, the second finds the row it wrote. Its mappings allow one report.
		runReconciliation(PROCESS_KEY_RECONCILIATION_NIGHTLY);
		runReconciliation(PROCESS_KEY_RECONCILIATION_NIGHTLY);

		verify(exactly(readsByTheProcess + 2), getRequestedFor(urlPathEqualTo(PROCESSES_PATH_NIGHTLY_INCIDENT)));
		verifyAllStubs();
	}

	/**
	 * The two rows the sweeps read. A reconciliation only counts a row as its own when the row carries the process
	 * instance id, and the engine generates that at runtime, so a static mapping file cannot hold it. Registered after
	 * the last setupCall of the test case, which is what loads the mapping files.
	 */
	private static void stubRowsOfTheErrand(final String processesPath, final String scenario, final String processInstanceId) {
		stubFor(get(urlPathEqualTo(processesPath))
			.inScenario(scenario)
			.whenScenarioStateIs(STARTED)
			.willSetStateTo("first-sweep-is-running")
			.willReturn(okJson("""
				{"processes":[{"processInstanceId":"%s","processKey":"low-alcohol-beer-sales","processStatus":"RUNNING"}]}""".formatted(processInstanceId))
				.withHeader("Content-Encoding", "identity")));

		stubFor(get(urlPathEqualTo(processesPath))
			.inScenario(scenario)
			.whenScenarioStateIs("incident-has-been-reported")
			.willReturn(okJson("""
				{"processes":[{"processInstanceId":"%s","processKey":"low-alcohol-beer-sales","processStatus":"FAILED","error":{"code":"INCIDENT"}}]}"""
				.formatted(processInstanceId))
				.withHeader("Content-Encoding", "identity")));
	}

	/** A sweep runs as its own process instance, so the test waits it out before looking at what it sent. */
	private void runReconciliation(final String processKey) {
		final var run = operatonClient.startProcessWithTenant(processKey, TENANT_ID_ALKT, new StartProcessInstanceDto());

		awaitProcessCompleted(run.getId());
	}
}
