package apptest.operaton;

import apptest.verification.Tuples;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import java.time.Duration;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static apptest.mock.api.SupportManagement.mockReportProcess;
import static apptest.mock.api.SupportManagement.reportPath;
import static apptest.verification.ProcessPathway.decisionPassThroughPathway;
import static apptest.verification.ProcessPathway.investigationPassThroughPathway;
import static apptest.verification.ProcessPathway.registrationPassThroughPathway;
import static apptest.verification.ProcessPathway.reviewPassThroughPathway;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.time.Duration.ZERO;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.setDefaultPollDelay;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.awaitility.Awaitility.setDefaultTimeout;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_ADDITION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_E_CIGARETTE_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_EXTERNAL_INSPECTION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_INTERNAL_INSPECTION;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CHANGE;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_TOBACCO_SALES_CLOSURE;

/**
 * Where each process first stops when nothing but the start event has happened, and what Support Management is told
 * about it. One test per process key, so a model that grows work steps gets a place to grow its test with it.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
class ProcessInitialWaitStateIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID_ALKT = "ALKT";
	// The errand processes plus process-reconciliation, which is deployed on its own and has no key in PROCESS_KEYS
	private static final int EXPECTED_DEPLOYMENTS = PROCESS_KEYS.size() + 1;
	private static final String ERRAND_EVENTS_PATH = "/%s/%s/process/errand-events".formatted(MUNICIPALITY_ID, NAMESPACE);

	@BeforeEach
	void setup() {
		mockApiGatewayToken();

		setDefaultPollInterval(500, MILLISECONDS);
		setDefaultPollDelay(ZERO);
		setDefaultTimeout(Duration.ofSeconds(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS));

		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.getDeployments(null, null, TENANT_ID_ALKT).size(), equalTo(EXPECTED_DEPLOYMENTS));
	}

	@Test
	void test001_alcoholServingStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_ALCOHOL_SERVING);
	}

	@Test
	void test002_alcoholServingChangeStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_ALCOHOL_SERVING_CHANGE);
	}

	@Test
	void test003_alcoholServingAdditionStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_ALCOHOL_SERVING_ADDITION);
	}

	@Test
	void test004_tobaccoSalesStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_TOBACCO_SALES);
	}

	@Test
	void test005_tobaccoSalesChangeStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_TOBACCO_SALES_CHANGE);
	}

	@Test
	void test006_tobaccoSalesClosureStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_TOBACCO_SALES_CLOSURE);
	}

	@Test
	void test007_eCigaretteSalesStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_E_CIGARETTE_SALES);
	}

	@Test
	void test008_externalInspectionStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_EXTERNAL_INSPECTION);
	}

	@Test
	void test009_internalInspectionStopsInRegistration() throws JacksonException {
		assertStopsInRegistration(PROCESS_KEY_INTERNAL_INSPECTION);
	}

	@Test
	void test010_lowAlcoholBeerServingRunsToFollowUp() throws JacksonException {
		assertRunsToFollowUp(PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING);
	}

	@Test
	void test011_lowAlcoholBeerSalesRunsToFollowUp() throws JacksonException {
		assertRunsToFollowUp(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES);
	}

	/**
	 * A model that waits for a case worker in every phase. The registration phase and its catch event are still running
	 * when the process parks, so the route holds the start event and the start of the phase and nothing else.
	 */
	private void assertStopsInRegistration(final String processKey) throws JacksonException {
		final var errandId = randomUUID().toString();
		final var processInstanceId = startAndAwaitWaitState(errandId, processKey, "await_registration_completed");

		assertProcessPathway(processInstanceId, false, Tuples.create()
			.with(tuple("Start process", "start_process"))
			.with(tuple("Start registration phase", "start_registration_phase")));

		assertWaitStateReported(errandId, processInstanceId, processKey, "registration_phase", "Registration", "registration_completed", "Registration completed");
	}

	/**
	 * A model whose first four phases hold no wait state. The route proves both halves of that: the four phases ended
	 * without a case worker, and the process is parked on the follow up gate rather than past it.
	 */
	private void assertRunsToFollowUp(final String processKey) throws JacksonException {
		final var errandId = randomUUID().toString();
		final var processInstanceId = startAndAwaitWaitState(errandId, processKey, "await_follow_up_completed");

		assertProcessPathway(processInstanceId, false, Tuples.create()
			.with(tuple("Start process", "start_process"))
			.with(registrationPassThroughPathway())
			.with(reviewPassThroughPathway())
			.with(investigationPassThroughPathway())
			.with(decisionPassThroughPathway())
			.with(tuple("Start follow up phase", "start_follow_up_phase")));

		assertWaitStateReported(errandId, processInstanceId, processKey, "follow_up_phase", "Follow up", "follow_up_completed", "Follow up completed");
	}

	/** The phase rather than the catch event, and the button the user interface is to show for it. */
	private void assertWaitStateReported(final String errandId, final String processInstanceId, final String processKey, final String phaseId, final String phaseName,
		final String signalName, final String signalLabel) {
		verify(putRequestedFor(urlPathEqualTo(reportPath(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId)))
			.withRequestBody(matchingJsonPath("$.processKey", WireMock.equalTo(processKey)))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("WAITING")))
			.withRequestBody(matchingJsonPath("$.currentActivityId", WireMock.equalTo(phaseId)))
			.withRequestBody(matchingJsonPath("$.currentActivityName", WireMock.equalTo(phaseName)))
			.withRequestBody(matchingJsonPath("$.awaitingSignals[0].name", WireMock.equalTo(signalName)))
			.withRequestBody(matchingJsonPath("$.awaitingSignals[0].label", WireMock.equalTo(signalLabel))));
	}

	/** Starts the process on an errand of its own and returns the instance once the engine has parked it on the gate. */
	private String startAndAwaitWaitState(final String errandId, final String processKey, final String activityId) throws JacksonException {
		mockReportProcess(MUNICIPALITY_ID, NAMESPACE, errandId);

		sendErrandEvent("""
			{"eventId": "%s", "eventType": "CREATE", "eventSubType": "ERRAND", "errandId": "%s",
			 "processKey": "%s", "startAllowed": true}""".formatted(randomUUID(), errandId, processKey));

		final var processInstanceId = awaitProcessInstance(errandId, processKey);

		awaitProcessState(processInstanceId, activityId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		return processInstanceId;
	}

	private void sendErrandEvent(final String body) throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(body)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();
	}

	/** The event carries no process id back, so the started instance is looked up the way Support Management would. */
	private String awaitProcessInstance(final String errandId, final String processKey) {
		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findProcessInstances(errandId, processKey, TENANT_ID_ALKT).size(), equalTo(1));

		return operatonClient.findProcessInstances(errandId, processKey, TENANT_ID_ALKT).getFirst().getId();
	}
}
