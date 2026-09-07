package apptest.operaton;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import tools.jackson.core.JacksonException;

import apptest.verification.Tuples;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.api.model.StartProcessResponse;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

/**
 * Standard happy-path flow without deviations. Each phase ends in a message catch event that the user interface
 * correlates when a case worker moves the errand on, so a started process parks in the registration phase and stays
 * there. The test asserts that it gets that far and waits where it should; driving it through the remaining phases
 * needs message correlation, which the service cannot do yet.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
class ProcessWithoutDeviationIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String TENANT_ID_ALKT = "ALKT";
	// One deployment per process model in processmodels/ - bump this when a process schema is added or removed
	private static final int EXPECTED_DEPLOYMENTS = 10;
	// The catch event ending the registration phase, where a started process comes to rest
	private static final String AWAIT_REGISTRATION_COMPLETED = "await_registration_completed";
	// Support Management identifies an errand by a UUID, so that is what the process is started with
	private static final String ERRAND_ID = "f0882f1d-06bc-47fd-b017-1d8307f5ce95";

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

		// Start process
		final var startResponse = setupCall()
			.withServicePath("/2281/ALKT/process/start/" + ERRAND_ID)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(ACCEPTED)
			.sendRequest()
			.andReturnBody(StartProcessResponse.class);

		// Wait for the process to park on the message the registration phase ends with
		awaitProcessState(startResponse.getProcessId(), AWAIT_REGISTRATION_COMPLETED, DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify wiremock stubs
		verifyAllStubs();

		// The route holds the events that have completed. The registration phase and its catch event are still running, so
		// neither is in it yet.
		assertProcessPathway(startResponse.getProcessId(), false, Tuples.create()
			.with(tuple("Start process", "start_process"))
			.with(tuple("Start registration phase", "start_registration_phase")));
	}
}
