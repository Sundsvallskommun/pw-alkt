package apptest.operaton;

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
 * Standard happy-path flow without deviations. Every phase of the ansokan process is still an empty subprocess, so the
 * process runs straight through; the assertions grow as the phases are implemented.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
class ProcessWithoutDeviationIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String TENANT_ID_ALKT = "ALKT";
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
			.until(() -> operatonClient.getDeployments(null, null, TENANT_ID_ALKT).size(), equalTo(1));
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

		// Wait for process to finish
		awaitProcessCompleted(startResponse.getProcessId(), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		// Verify wiremock stubs
		verifyAllStubs();

		// Verify process pathway.
		assertProcessPathway(startResponse.getProcessId(), false, Tuples.create()
			.with(tuple("Start process", "start_process"))
			.with(registrationPathway())
			.with(reviewPathway())
			.with(investigationPathway())
			.with(decisionPathway())
			.with(followUpPathway())
			.with(closurePathway())
			.with(tuple("End process", "end_process")));
	}
}
