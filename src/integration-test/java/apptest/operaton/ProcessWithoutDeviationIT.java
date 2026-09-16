package apptest.operaton;

import apptest.verification.Tuples;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static apptest.mock.api.SupportManagement.mockReportProcess;
import static apptest.mock.api.SupportManagement.reportPath;
import static apptest.verification.ProcessPathway.closurePathway;
import static apptest.verification.ProcessPathway.decisionPassThroughPathway;
import static apptest.verification.ProcessPathway.decisionPathway;
import static apptest.verification.ProcessPathway.followUpPathway;
import static apptest.verification.ProcessPathway.investigationPassThroughPathway;
import static apptest.verification.ProcessPathway.investigationPathway;
import static apptest.verification.ProcessPathway.registrationPassThroughPathway;
import static apptest.verification.ProcessPathway.registrationPathway;
import static apptest.verification.ProcessPathway.reviewPassThroughPathway;
import static apptest.verification.ProcessPathway.reviewPathway;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.time.Duration.ZERO;
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
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_SUPERVISION;

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
class ProcessWithoutDeviationIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID_ALKT = "ALKT";
	// The errand processes plus process-reconciliation, which is deployed on its own and has no key in PROCESS_KEYS
	private static final int EXPECTED_DEPLOYMENTS = PROCESS_KEYS.size() + 1;
	private static final String ERRAND_EVENTS_PATH = "/%s/%s/process/errand-events".formatted(MUNICIPALITY_ID, NAMESPACE);
	// The models holding no wait state before the follow up phase, so nobody signals their first four phases
	private static final Set<String> PASS_THROUGH_KEYS = Set.of(PROCESS_KEY_LOW_ALCOHOL_BEER_SALES, PROCESS_KEY_LOW_ALCOHOL_BEER_SERVING);
	// The name attribute every model gives its phase, and the stem of the name its catch event carries
	private static final Map<String, String> PHASE_DISPLAY_NAMES = Map.of(
		"registration", "Registration",
		"review", "Review",
		"investigation", "Investigation",
		"decision", "Decision",
		"follow_up", "Follow up",
		"closure", "Closure");

	/** Sorted, so a failing run names the same process in the same place every time. */
	static Stream<String> processKeys() {
		return PROCESS_KEYS.stream().sorted();
	}

	@BeforeEach
	void setup() {
		setDefaultPollInterval(500, MILLISECONDS);
		setDefaultPollDelay(ZERO);
		setDefaultTimeout(Duration.ofSeconds(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS));

		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.getDeployments(null, null, TENANT_ID_ALKT).size(), equalTo(EXPECTED_DEPLOYMENTS));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("processKeys")
	void test001_createProcess(final String processKey) throws JacksonException, ClassNotFoundException {

		final var errandId = randomId();

		mockApiGatewayToken();
		mockReportProcess(MUNICIPALITY_ID, NAMESPACE, errandId);

		sendErrandEvent(startEventFor(errandId, processKey));

		final var processInstanceId = awaitProcessInstance(errandId, processKey);

		for (final var phase : waitingPhasesOf(processKey)) {
			completePhase(errandId, processInstanceId, processKey, phase);
		}

		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// The last work step is the one that tells Support Management the process is over
		verifyAllStubs();
		verify(putRequestedFor(urlPathEqualTo("/api-support-management/%s/%s/errands/%s/processes/%s".formatted(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId)))
			.withHeader("X-Sent-By", WireMock.equalTo("pw-alkt; type=processEngine"))
			.withRequestBody(matchingJsonPath("$.processService", WireMock.equalTo("pw-alkt")))
			.withRequestBody(matchingJsonPath("$.processKey", WireMock.equalTo(processKey)))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("COMPLETED")))
			.withRequestBody(matchingJsonPath("$.currentActivityId", WireMock.equalTo("external_task_complete_process")))
			.withRequestBody(matchingJsonPath("$.externalTaskId", matching("[0-9a-f-]{36}")))
			.withRequestBody(matchingJsonPath("$.processInstanceId", absent())));

		assertProcessPathway(processInstanceId, false, expectedPathway(processKey));

		assertWaitStatesReported(errandId, processInstanceId, processKey);
	}

	/**
	 * One WAITING report per phase that waits: the first from the start, the rest from each signal but the last, which
	 * runs the process to its end. The activity is the phase and the signal carries the name of its catch event.
	 */
	private void assertWaitStatesReported(final String errandId, final String processInstanceId, final String processKey) {
		final var reportPath = reportPath(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId);
		final var waitingPhases = waitingPhasesOf(processKey);

		verify(exactly(waitingPhases.size()), putRequestedFor(urlPathEqualTo(reportPath))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("WAITING"))));

		waitingPhases.forEach(phase -> verify(putRequestedFor(urlPathEqualTo(reportPath))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("WAITING")))
			.withRequestBody(matchingJsonPath("$.currentActivityId", WireMock.equalTo("%s_phase".formatted(phase))))
			.withRequestBody(matchingJsonPath("$.currentActivityName", WireMock.equalTo(displayNameOf(phase))))
			.withRequestBody(matchingJsonPath("$.awaitingSignals[0].name", WireMock.equalTo("%s_completed".formatted(phase))))
			.withRequestBody(matchingJsonPath("$.awaitingSignals[0].label", WireMock.equalTo("%s completed".formatted(displayNameOf(phase)))))));
	}

	/** Spelled out rather than derived from the phase id, so a model renaming a phase fails here instead of passing. */
	private static String displayNameOf(final String phase) {
		return PHASE_DISPLAY_NAMES.get(phase);
	}

	private static List<String> waitingPhasesOf(final String processKey) {
		if (PASS_THROUGH_KEYS.contains(processKey)) {
			return List.of("follow_up", "closure");
		}

		return List.of("registration", "review", "investigation", "decision", "follow_up", "closure");
	}

	private static Tuples expectedPathway(final String processKey) {
		final var pathway = Tuples.create()
			.with(tuple("Start process", "start_process"));

		if (PASS_THROUGH_KEYS.contains(processKey)) {
			pathway
				.with(registrationPassThroughPathway())
				.with(reviewPassThroughPathway())
				.with(investigationPassThroughPathway())
				.with(decisionPassThroughPathway());
		} else {
			pathway
				.with(registrationPathway())
				.with(reviewPathway())
				.with(investigationPathway())
				.with(decisionPathway());
		}

		return pathway
			.with(followUpPathway())
			.with(closurePathway())
			.with(tuple("Complete process", "external_task_complete_process"))
			.with(tuple("End process", "end_process"));
	}

	/**
	 * A supervision is started by hand, and that command reaches this service as an event with subtype PROCESS. The
	 * start path never reads the subtype, so the difference between the two events is documentation.
	 */
	private static String startEventFor(final String errandId, final String processKey) {
		if (PROCESS_KEY_SUPERVISION.equals(processKey)) {
			return """
				{"eventId": "%s", "eventType": "UPDATE", "eventSubType": "PROCESS", "errandId": "%s",
				 "processKey": "%s", "startAllowed": true}""".formatted(randomId(), errandId, processKey);
		}

		return """
			{"eventId": "%s", "eventType": "CREATE", "eventSubType": "ERRAND", "errandId": "%s",
			 "processKey": "%s", "startAllowed": true}""".formatted(randomId(), errandId, processKey);
	}

	/**
	 * Waits until the process is parked on the catch event ending the phase, then sends the signal event Support
	 * Management publishes when a case worker moves the errand on.
	 */
	private void completePhase(final String errandId, final String processInstanceId, final String processKey, final String phase) throws JacksonException {
		awaitProcessState(processInstanceId, "await_%s_completed".formatted(phase), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		sendErrandEvent("""
			{"eventId": "%s", "eventType": "UPDATE", "eventSubType": "SIGNAL", "errandId": "%s",
			 "processKey": "%s", "startAllowed": false, "signalName": "%s_completed"}"""
			.formatted(randomId(), errandId, processKey, phase));
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

	private static String randomId() {
		return UUID.randomUUID().toString();
	}
}
