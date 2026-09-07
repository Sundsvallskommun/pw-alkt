package apptest.operaton;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import tools.jackson.core.JacksonException;

import apptest.verification.Tuples;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static apptest.verification.ProcessPathway.closurePathway;
import static apptest.verification.ProcessPathway.decisionPathway;
import static apptest.verification.ProcessPathway.followUpPathway;
import static apptest.verification.ProcessPathway.investigationPathway;
import static apptest.verification.ProcessPathway.registrationPathway;
import static apptest.verification.ProcessPathway.reviewPathway;
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

@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
class ProcessWithoutDeviationIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID_ALKT = "ALKT";
	// One deployment per process model in processmodels/ - bump this when a process schema is added or removed
	private static final int EXPECTED_DEPLOYMENTS = 10;
	// Support Management identifies an errand by a UUID, so that is what the process is started with
	private static final String ERRAND_ID = "f0882f1d-06bc-47fd-b017-1d8307f5ce95";
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String ERRAND_EVENTS_PATH = "/%s/%s/process/errand-events".formatted(MUNICIPALITY_ID, NAMESPACE);

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

	@Test
	void test001_createProcess() throws JacksonException, ClassNotFoundException {

		// Setup mocks
		mockApiGatewayToken();

		// The errand was created and Support Management allows the event to start a process
		sendErrandEvent("""
			{"eventId": "%s", "eventType": "CREATE", "eventSubType": "ERRAND", "errandId": "%s",
			 "processKey": "%s", "startAllowed": true}""".formatted(randomEventId(), ERRAND_ID, PROCESS_KEY));

		final var processInstanceId = awaitProcessInstance();

		// Step the process through its phases the way a case worker does, one signal per phase
		completePhase(processInstanceId, "registration");
		completePhase(processInstanceId, "review");
		completePhase(processInstanceId, "investigation");
		completePhase(processInstanceId, "decision");
		completePhase(processInstanceId, "follow_up");
		completePhase(processInstanceId, "closure");

		// Wait for process to finish
		awaitProcessCompleted(processInstanceId, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify wiremock stubs
		verifyAllStubs();

		// Verify process pathway.
		assertProcessPathway(processInstanceId, false, Tuples.create()
			.with(tuple("Start process", "start_process"))
			.with(registrationPathway())
			.with(reviewPathway())
			.with(investigationPathway())
			.with(decisionPathway())
			.with(followUpPathway())
			.with(closurePathway())
			.with(tuple("End process", "end_process")));
	}

	/**
	 * Waits until the process is parked on the catch event ending the phase, then sends the signal event Support
	 * Management publishes when a case worker moves the errand on.
	 */
	private void completePhase(final String processInstanceId, final String phase) throws JacksonException, ClassNotFoundException {
		awaitProcessState(processInstanceId, "await_%s_completed".formatted(phase), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		sendErrandEvent("""
			{"eventId": "%s", "eventType": "UPDATE", "eventSubType": "SIGNAL", "errandId": "%s",
			 "processKey": "%s", "startAllowed": false, "signalName": "%s_completed"}"""
			.formatted(randomEventId(), ERRAND_ID, PROCESS_KEY, phase));
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
	private String awaitProcessInstance() {
		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findProcessInstances(ERRAND_ID, PROCESS_KEY, TENANT_ID_ALKT).size(), equalTo(1));

		return operatonClient.findProcessInstances(ERRAND_ID, PROCESS_KEY, TENANT_ID_ALKT).getFirst().getId();
	}

	private static String randomEventId() {
		return java.util.UUID.randomUUID().toString();
	}
}
