package apptest.operaton;

import com.github.tomakehurst.wiremock.client.WireMock;
import generated.se.sundsvall.operaton.StartProcessInstanceDto;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static apptest.mock.api.SupportManagement.mockGetErrandProcesses;
import static apptest.mock.api.SupportManagement.mockOtherErrandsGone;
import static apptest.mock.api.SupportManagement.mockReportProcess;
import static apptest.mock.api.SupportManagement.mockReportProcessFails;
import static apptest.mock.api.SupportManagement.reportPath;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.time.Duration.ZERO;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.setDefaultPollDelay;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.awaitility.Awaitility.setDefaultTimeout;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEY_RECONCILIATION;

/**
 * The reconciliation is started by hand rather than waited for: its timer fires every five minutes, and the test has
 * no reason to wait that long. max.retries=0 makes the first failing report raise an incident right away.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
@TestPropertySource(properties = {
	"reconciliation.worker.enabled=true",
	"camunda.worker.max.retries=0"
})
class ProcessReconciliationIT extends AbstractOperatonAppTest {

	private static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String TENANT_ID_ALKT = "ALKT";
	private static final int EXPECTED_DEPLOYMENTS = 11;
	private static final String PROCESS_KEY = "alcohol-serving";
	private static final String ERRAND_EVENTS_PATH = "/%s/%s/process/errand-events".formatted(MUNICIPALITY_ID, NAMESPACE);
	private static final String ALL_PROCESS_KEYS = "alcohol-serving,alcohol-serving-addition,alcohol-serving-change,e-cigarette-sales,low-alcohol-beer-sales,low-alcohol-beer-serving,supervision,tobacco-sales,tobacco-sales-change,tobacco-sales-closure";

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
	void test001_incidentIsReportedOnce() throws JacksonException {
		final var errandId = randomId();
		mockApiGatewayToken();
		mockOtherErrandsGone();

		// The last step cannot report: Support Management is down, and with no retries the engine raises an incident
		mockReportProcessFails(MUNICIPALITY_ID, NAMESPACE, errandId);
		final var processInstanceId = startProcess(errandId);
		for (final var phase : new String[] {
			"registration", "review", "investigation", "decision", "follow_up", "closure"
		}) {
			completePhase(errandId, processInstanceId, phase);
		}
		await().until(() -> operatonClient.findIncidents(TENANT_ID_ALKT, ALL_PROCESS_KEYS).stream().anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId())));

		// Support Management is back and still says RUNNING, so the reconciliation reports the incident
		wiremock.resetRequests();
		mockReportProcess(MUNICIPALITY_ID, NAMESPACE, errandId);
		mockGetErrandProcesses(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId, "RUNNING", null);
		runReconciliation();

		verify(exactly(1), putRequestedFor(urlPathEqualTo(reportPath(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId)))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("FAILED")))
			.withRequestBody(matchingJsonPath("$.error.code", WireMock.equalTo("INCIDENT")))
			.withRequestBody(matchingJsonPath("$.currentActivityId", WireMock.equalTo("external_task_complete_process")))
			.withRequestBody(matchingJsonPath("$.externalTaskId"))
			.withRequestBody(matchingJsonPath("$.activities[0].activityType", WireMock.equalTo("INCIDENT"))));

		// Now the row says so, and the next run leaves it alone
		mockGetErrandProcesses(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId, "FAILED", "INCIDENT");
		runReconciliation();

		verify(exactly(1), putRequestedFor(urlPathEqualTo(reportPath(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId))));
	}

	@Test
	void test002_instanceThatVanishedIsSettled() throws JacksonException {
		final var errandId = randomId();
		mockApiGatewayToken();
		mockOtherErrandsGone();
		mockReportProcess(MUNICIPALITY_ID, NAMESPACE, errandId);

		// Someone cancels the instance from outside, and nothing reports it
		final var processInstanceId = startProcess(errandId);
		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		operatonClient.deleteProcessInstance(processInstanceId, false);

		mockGetErrandProcesses(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId, "RUNNING", null);
		runReconciliation();

		verify(exactly(1), putRequestedFor(urlPathEqualTo(reportPath(MUNICIPALITY_ID, NAMESPACE, errandId, processInstanceId)))
			.withRequestBody(matchingJsonPath("$.processStatus", WireMock.equalTo("FAILED")))
			.withRequestBody(matchingJsonPath("$.error.code", WireMock.equalTo("TERMINATED")))
			.withRequestBody(matchingJsonPath("$.activities[0].activityType", WireMock.equalTo("RECONCILIATION"))));
	}

	private void runReconciliation() {
		final var run = operatonClient.startProcessWithTenant(PROCESS_KEY_RECONCILIATION, TENANT_ID_ALKT, new StartProcessInstanceDto());
		awaitProcessCompleted(run.getId(), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
	}

	private String startProcess(final String errandId) throws JacksonException {
		sendErrandEvent("""
			{"eventId": "%s", "eventType": "CREATE", "eventSubType": "ERRAND", "errandId": "%s",
			 "processKey": "%s", "startAllowed": true}""".formatted(randomId(), errandId, PROCESS_KEY));

		await()
			.ignoreExceptions()
			.until(() -> operatonClient.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID_ALKT).size(), equalTo(1));

		return operatonClient.findProcessInstances(errandId, PROCESS_KEY, TENANT_ID_ALKT).getFirst().getId();
	}

	private void completePhase(final String errandId, final String processInstanceId, final String phase) throws JacksonException {
		awaitProcessState(processInstanceId, "await_%s_completed".formatted(phase), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		sendErrandEvent("""
			{"eventId": "%s", "eventType": "UPDATE", "eventSubType": "SIGNAL", "errandId": "%s",
			 "processKey": "%s", "startAllowed": false, "signalName": "%s_completed"}"""
			.formatted(randomId(), errandId, PROCESS_KEY, phase));
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

	private static String randomId() {
		return java.util.UUID.randomUUID().toString();
	}
}
