package apptest;

import generated.se.sundsvall.operaton.ExternalTaskDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;
import static se.sundsvall.alkt.service.model.ProcessType.CATERING_OCCASION;

/**
 * A step that stands on an incident is tried once more when the case worker changes the errand. Catering occasion is
 * used since its first work step raises the incident at once on an errand without a permit holder.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/RetryIncidentIT/", classes = Application.class)
class RetryIncidentIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "9696b9e6-6753-48ca-aa9d-cd1ca7f18d70";
	private static final String STUCK_ACTIVITY = "external_task_create_decision";
	private static final String SLACK_PATH = "/api-messaging/2281/slack";
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

	@Test
	void test001_errandChangeRetriesTheStuckStep() throws JacksonException {
		final var processInstanceId = startProcessAndAwaitIncident();

		// The case worker adds the permit holder
		wiremock.setScenarioState("errand", "corrected");
		sendErrandEvent("ERRAND");

		assertThat(operatonClient.findExternalTasks(processInstanceId, true)).isEmpty();
		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("follow_up_phase");

		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, CATERING_OCCASION.getKey()))
			.noneMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()));
		wiremock.verify(1, postRequestedFor(urlPathEqualTo(SLACK_PATH)));
		verifyAllStubs();
	}

	@Test
	void test002_stepThatFailsAgainLeavesANewIncident() throws JacksonException {
		final var processInstanceId = startProcessAndAwaitIncident();

		// The errand is changed, but still has no permit holder
		sendErrandEvent("ERRAND");

		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> wiremock.findAll(postRequestedFor(urlPathEqualTo(SLACK_PATH))).size() == 2);

		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, CATERING_OCCASION.getKey()))
			.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()) && STUCK_ACTIVITY.equals(incident.getActivityId()));
		assertThat(operatonClient.findExternalTasks(processInstanceId, true))
			.extracting(ExternalTaskDto::getActivityId)
			.containsExactly(STUCK_ACTIVITY);
		assertThat(countReports("$[?(@.processStatus == 'FAILED')]")).isEqualTo(2);
		assertThat(countReports("$[?(@.processStatus == 'RUNNING' && @.currentActivityId == '%s')]".formatted(STUCK_ACTIVITY))).isEqualTo(2);
		verifyAllStubs();
	}

	@Test
	void test003_otherChangeLeavesTheStepStuck() throws JacksonException {
		final var processInstanceId = startProcessAndAwaitIncident();

		sendErrandEvent("MESSAGE");

		assertThat(operatonClient.findExternalTasks(processInstanceId, true))
			.extracting(ExternalTaskDto::getActivityId)
			.containsExactly(STUCK_ACTIVITY);
		assertThat(countReports("$[?(@.processStatus == 'RUNNING' && @.currentActivityId == '%s')]".formatted(STUCK_ACTIVITY))).isEqualTo(1);
		verifyAllStubs();
	}

	@Test
	void test004_errandChangeLeavesAProcessWithoutIncidentWhereItWaits() throws JacksonException {
		startProcess();
		final var processInstanceId = awaitProcessInstance(ERRAND_ID, CATERING_OCCASION.getKey());
		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("follow_up_phase");

		sendErrandEvent("ERRAND");

		awaitProcessState(processInstanceId, "await_follow_up_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		assertThat(operatonClient.findExternalTasks(processInstanceId, true)).isEmpty();
		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, CATERING_OCCASION.getKey()))
			.noneMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()));
		verifyAllStubs();
	}

	private String startProcessAndAwaitIncident() {
		startProcess();
		final var processInstanceId = awaitProcessInstance(ERRAND_ID, CATERING_OCCASION.getKey());
		awaitIncidentAt(CATERING_OCCASION.getKey(), processInstanceId, STUCK_ACTIVITY);
		assertThat(operatonClient.findExternalTasks(processInstanceId, true))
			.extracting(ExternalTaskDto::getActivityId)
			.containsExactly(STUCK_ACTIVITY);
		return processInstanceId;
	}

	private void startProcess() {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();
	}

	private void sendErrandEvent(final String eventSubType) throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(JSON_MAPPER.writeValueAsString(ErrandEvent.create()
				.withEventId(UUID.randomUUID().toString())
				.withEventType(UPDATE)
				.withEventSubType(eventSubType)
				.withErrandId(ERRAND_ID)
				.withProcessKey(CATERING_OCCASION.getKey())
				.withStartAllowed(false)))
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();
	}
}
