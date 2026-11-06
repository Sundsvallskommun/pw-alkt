package apptest;

import generated.se.sundsvall.operaton.ExternalTaskDto;
import generated.se.sundsvall.operaton.ProcessInstanceDto;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;
import static se.sundsvall.alkt.service.model.ProcessType.CATERING_OCCASION;

/**
 * A step that stands on an incident is tried once more when the case worker changes the errand, or starts it again.
 * Catering occasion is used since its first work step raises the incident at once on an errand without a permit holder.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/RetryIncidentIT/", classes = Application.class, sharedContext = false)
class RetryIncidentIT extends AbstractOperatonAppTest {

	private static final String ERRAND_ID = "9696b9e6-6753-48ca-aa9d-cd1ca7f18d70";
	private static final String STUCK_ACTIVITY = "external_task_create_decision";
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

	@Test
	void test001_errandChangeRetriesTheStuckStep() throws JacksonException {
		// The case worker adds the permit holder
		assertStuckStepRetriedBy("ERRAND", false);
	}

	@Test
	void test002_startAgainRetriesTheStuckStep() throws JacksonException {
		// The case worker corrects the errand by a change that sends no event, and starts the errand again. Support
		// Management allows the start since it holds the process as failed.
		assertStuckStepRetriedBy("PROCESS", true);
	}

	private void assertStuckStepRetriedBy(final String eventSubType, final boolean startAllowed) throws JacksonException {
		final var processInstanceId = startProcess(ERRAND_ID, CATERING_OCCASION.getKey());
		completePhase(ERRAND_ID, processInstanceId, CATERING_OCCASION.getKey(), "registration");
		awaitIncidentAt(CATERING_OCCASION.getKey(), processInstanceId, STUCK_ACTIVITY);
		assertThat(operatonClient.findExternalTasks(processInstanceId, true))
			.extracting(ExternalTaskDto::getActivityId)
			.containsExactly(STUCK_ACTIVITY);

		wiremock.setScenarioState("errand", "corrected");
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(JSON_MAPPER.writeValueAsString(ErrandEvent.create()
				.withEventId(UUID.randomUUID().toString())
				.withEventType(UPDATE)
				.withEventSubType(eventSubType)
				.withErrandId(ERRAND_ID)
				.withProcessKey(CATERING_OCCASION.getKey())
				.withStartAllowed(startAllowed)))
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		assertThat(operatonClient.findExternalTasks(processInstanceId, true)).isEmpty();
		awaitProcessState(processInstanceId, "await_follow_up_completed");
		awaitReportAt("follow_up_phase");

		assertThat(operatonClient.findProcessInstances(ERRAND_ID, CATERING_OCCASION.getKey(), TENANT_ID_ALKT))
			.extracting(ProcessInstanceDto::getId)
			.containsExactly(processInstanceId);
		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, CATERING_OCCASION.getKey()))
			.noneMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()));
		wiremock.verify(1, postRequestedFor(urlPathEqualTo("/api-messaging/2281/slack")));
		verifyAllStubs();
	}
}
