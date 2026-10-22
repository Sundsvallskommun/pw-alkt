package apptest;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import tools.jackson.core.JacksonException;

import java.util.Comparator;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;

/**
 * The PDF of the submission, the first step of the registration. Run on the alcohol serving process, which waits in the
 * registration right after the step. One retry, so a failing step becomes an incident on its second attempt.
 */
@DirtiesContext
@WireMockAppTestSuite(files = "classpath:/SubmissionPdfIT/", classes = Application.class)
@TestPropertySource(properties = {
	"camunda.worker.max.retries=1",
	"camunda.worker.retry.timeout=100"
})
class SubmissionPdfIT extends AbstractOperatonAppTest {

	private static final String REQUEST_FILE = "request.json";
	private static final String ERRAND_ID = "5b2c8e14-7d3a-4f91-b6e0-3a9c1d2e4f70";
	private static final String STEP_ID = "external_task_create_submission_pdf";
	private static final String ATTACHMENTS_PATH = "/api-support-management/2281/ALKT/errands/%s/attachments".formatted(ERRAND_ID);
	private static final String TEMPLATING_PATH = "/api-templating/2281/render/pdf";
	private static final String JSON_SCHEMA_PATH = "/api-json-schema/2281/schemas/.*";
	private static final String PROCESSES_PATH = "/api-support-management/2281/ALKT/errands/%s/processes/[^/]+".formatted(ERRAND_ID);
	private static final String DONE_ENTRY = "$.activities[?(@.activityId == 'external_task_create_submission_pdf#done')]";
	private static final String SKIPPED_ENTRY = "$.activities[?(@.activityId =~ /external_task_create_submission_pdf#[0-9]+/ && @.errorCode == 'SKIPPED')]";

	@Test
	void test001_pdfIsAttachedBeforeTheRegistrationWaits() throws JacksonException {
		final var processInstanceId = startProcess();

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("registration_phase");

		// The mappings assert every call: schema and UI schema of the schemaId of the first JSON parameter, the template
		// alkt.form-pdf 1.0 with the title and sections, the upload under its name and the done entry in the log
		verifyAllStubs();
		wiremock.verify(exactly(1), postRequestedFor(urlPathEqualTo(TEMPLATING_PATH)));
		wiremock.verify(exactly(1), postRequestedFor(urlPathEqualTo(ATTACHMENTS_PATH)));
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH)).withRequestBody(matchingJsonPath(DONE_ENTRY)));

		// The PDF is on the errand before the process reports that it waits in the registration
		final var events = wiremock.getAllServeEvents().stream().sorted(Comparator.comparing(event -> event.getRequest().getLoggedDate())).toList();
		final var upload = indexOf(events, "POST", ATTACHMENTS_PATH);
		final var waiting = events.stream()
			.filter(event -> "PUT".equals(event.getRequest().getMethod().getName()) && event.getRequest().getBodyAsString().contains("\"WAITING\""))
			.map(events::indexOf)
			.findFirst()
			.orElseThrow();
		assertThat(upload).isLessThan(waiting);

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", STEP_ID));
	}

	@Test
	void test002_errandWithoutJsonParametersSkipsTheStep() throws JacksonException {
		final var processInstanceId = startProcess();

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("registration_phase");

		// The skipped entry and the WAITING report are asserted by their mappings
		verifyAllStubs();
		wiremock.verify(exactly(0), getRequestedFor(urlPathMatching(JSON_SCHEMA_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(TEMPLATING_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(ATTACHMENTS_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo("/api-messaging/2281/slack")));
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH)).withRequestBody(matchingJsonPath(SKIPPED_ENTRY)));
		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, ALCOHOL_SERVING.getKey()))
			.noneMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()));

		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrder(
				tuple("Start process", "start_process"),
				tuple("Start registration phase", "start_registration_phase"),
				tuple("Create submission PDF", STEP_ID),
				tuple("No submission", "boundary_create_submission_pdf_skipped"));
	}

	@Test
	void test003_pdfAlreadyOnTheErrandIsNotCreatedAgain() throws JacksonException {
		final var processInstanceId = startProcess();

		awaitProcessState(processInstanceId, "await_registration_completed", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);
		awaitReportAt("registration_phase");

		verifyAllStubs();
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH)).withRequestBody(matchingJsonPath(DONE_ENTRY)));
		wiremock.verify(exactly(0), getRequestedFor(urlPathMatching(JSON_SCHEMA_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(TEMPLATING_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(ATTACHMENTS_PATH)));
	}

	@Test
	void test004_schemaThatDoesNotExistIsAnIncidentAtOnce() throws JacksonException {
		final var processInstanceId = startProcess();

		awaitIncidentAt(ALCOHOL_SERVING.getKey(), processInstanceId, STEP_ID);

		// The FAILED report says the attempt was not retried
		verifyAllStubs();
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH))
			.withRequestBody(matchingJsonPath("$.processStatus", equalTo("FAILED")))
			.withRequestBody(matchingJsonPath("$.activities[0].errorCode", equalTo("INCIDENT")))
			.withRequestBody(matchingJsonPath("$.activities[0].message", containing("Attempt 1, not retried"))));
		wiremock.verify(exactly(1), getRequestedFor(urlPathEqualTo("/api-json-schema/2281/schemas/2281_aot_alcohol_folkol_sales_notification_0.11")));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(TEMPLATING_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(ATTACHMENTS_PATH)));
		assertThat(operatonClient.getEventSubscriptions(processInstanceId, null)).noneMatch(subscription -> "await_registration_completed".equals(subscription.getActivityId()));
	}

	@Test
	void test005_failingTemplatingIsRetriedAndThenAnIncident() throws JacksonException {
		final var processInstanceId = startProcess();

		awaitIncidentAt(ALCOHOL_SERVING.getKey(), processInstanceId, STEP_ID);

		// One retry: two attempts, the RETRYING report of the first and the FAILED report of the second
		verifyAllStubs();
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH)).withRequestBody(matchingJsonPath("$.processStatus", equalTo("RETRYING"))));
		wiremock.verify(exactly(1), putRequestedFor(urlPathMatching(PROCESSES_PATH)).withRequestBody(matchingJsonPath("$.processStatus", equalTo("FAILED"))));
		wiremock.verify(exactly(2), postRequestedFor(urlPathEqualTo(TEMPLATING_PATH)));
		wiremock.verify(exactly(0), postRequestedFor(urlPathEqualTo(ATTACHMENTS_PATH)));

		// The incident stands, and the process does not go on to wait in the registration
		await().during(2, SECONDS).atMost(5, SECONDS).until(() -> operatonClient.getEventSubscriptions(processInstanceId, null).stream()
			.noneMatch(subscription -> "await_registration_completed".equals(subscription.getActivityId())));
		assertThat(operatonClient.findIncidents(TENANT_ID_ALKT, ALCOHOL_SERVING.getKey()))
			.anyMatch(incident -> processInstanceId.equals(incident.getProcessInstanceId()) && STEP_ID.equals(incident.getActivityId()));
	}

	private String startProcess() throws JacksonException {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(REQUEST_FILE)
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();

		return awaitProcessInstance(ERRAND_ID, ALCOHOL_SERVING.getKey());
	}

	private static int indexOf(final java.util.List<ServeEvent> events, final String method, final String path) {
		return events.stream()
			.filter(event -> method.equals(event.getRequest().getMethod().getName()) && path.equals(event.getRequest().getUrl().split("\\?")[0]))
			.map(events::indexOf)
			.findFirst()
			.orElseThrow();
	}
}
