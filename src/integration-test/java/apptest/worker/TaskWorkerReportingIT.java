package apptest.worker;

import java.util.Map;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.Constants;
import se.sundsvall.alkt.api.model.ProcessStateReport;
import se.sundsvall.alkt.businesslogic.handler.FailureHandler;
import se.sundsvall.alkt.businesslogic.worker.AbstractTaskWorker;
import se.sundsvall.alkt.businesslogic.worker.CompleteProcessWorker;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.alkt.service.ProcessReportService;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static apptest.mock.api.SupportManagement.reportPath;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
@TestPropertySource(properties = {
	"process-engine.deployment.autoDeployEnabled=false",
	"camunda.bpm.client.disable-auto-fetching=true"
})
class TaskWorkerReportingIT extends AbstractAppTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "f0882f1d-06bc-47fd-b017-1d8307f5ce95";
	private static final String PROCESS_INSTANCE_ID = "8f1c2b6e-1f4a-4d61-9a0e-2b7c1f0a5e33";
	private static final String EXTERNAL_TASK_ID = "a91c7f30-4d2b-11f0-9e21-0242ac120004";
	private static final String ERRAND_PATH = "/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
	private static final String REPORT_PATH = reportPath(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID);

	private static final int SPECIFIC = 1;
	private static final int FALLBACK = 10;

	@Autowired
	private CompleteProcessWorker completeProcessWorker;

	@Autowired
	private ProcessReportService processReportService;

	@Autowired
	private FailureHandler failureHandler;

	@Autowired
	private SupportManagementClient supportManagementClient;

	@BeforeEach
	void setIdentity() {
		RequestId.init("test-request-id");
	}

	@AfterEach
	void clearIdentity() {
		RequestId.reset();
	}

	private ExternalTask mockTask() {
		final var task = mock(ExternalTask.class);
		when(task.getVariable(Constants.PROCESS_VARIABLE_MUNICIPALITY_ID)).thenReturn(MUNICIPALITY_ID);
		when(task.getVariable(Constants.PROCESS_VARIABLE_NAMESPACE)).thenReturn(NAMESPACE);
		when(task.getVariable(Constants.PROCESS_VARIABLE_ERRAND_ID)).thenReturn(ERRAND_ID);
		when(task.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);
		when(task.getProcessDefinitionKey()).thenReturn(Constants.PROCESS_KEY_ALCOHOL_SERVING);
		when(task.getId()).thenReturn(EXTERNAL_TASK_ID);
		when(task.getActivityId()).thenReturn("external_task_complete_process");
		// A mock answers 0 for Integer, which FailureHandler reads as the last attempt; a task with attempts left reports RETRYING
		when(task.getRetries()).thenReturn(3);
		return task;
	}

	private static void mockReportAccepted() {
		stubFor(put(urlEqualTo(REPORT_PATH)).atPriority(FALLBACK)
			.willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));
	}

	@Test
	void reportsRunningBeforeTheStepAndTheFinalStatusAfterIt() {
		mockApiGatewayToken();
		mockReportAccepted();

		final var task = mockTask();
		final var taskService = mock(ExternalTaskService.class);

		completeProcessWorker.execute(task, taskService);

		// RUNNING before the step, COMPLETED after it; the order itself is guaranteed by AbstractTaskWorker's synchronous
		// execute() and is unit-tested in CompleteProcessWorkerTest.
		verify(exactly(2), putRequestedFor(urlEqualTo(REPORT_PATH)));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH)).withRequestBody(matchingJsonPath("$.processStatus", equalTo("RUNNING"))));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH)).withRequestBody(matchingJsonPath("$.processStatus", equalTo("COMPLETED"))));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH)).withRequestBody(matchingJsonPath("$.processKey", equalTo(Constants.PROCESS_KEY_ALCOHOL_SERVING))));
		verify(taskService).complete(task, Map.of());
	}

	@Test
	void aPreconditionFailedOnTheReportRetriesTheStepAndRereadsTheErrand() {
		mockApiGatewayToken();

		stubFor(get(urlEqualTo(ERRAND_PATH))
			.inScenario("errand-version")
			.whenScenarioStateIs(STARTED)
			.willReturn(okJson("{\"version\":7}").withHeader("Content-Encoding", "identity"))
			.willSetStateTo("stale"));
		stubFor(get(urlEqualTo(ERRAND_PATH))
			.inScenario("errand-version")
			.whenScenarioStateIs("stale")
			.willReturn(okJson("{\"version\":8}").withHeader("Content-Encoding", "identity")));

		// A report carrying the stale version is refused; every other report (no version, or the fresh one) is accepted
		mockReportAccepted();
		stubFor(put(urlEqualTo(REPORT_PATH)).atPriority(SPECIFIC)
			.withRequestBody(matchingJsonPath("$.errandVersion", equalTo("7")))
			.willReturn(aResponse()
				.withStatus(412)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Precondition Failed\",\"status\":412}")));

		final var task = mockTask();
		final var taskService = mock(ExternalTaskService.class);

		// A step that reads the errand before reporting - stands in for a future step that writes business data, per
		// DRAKEN-4745's design doc. It always rereads rather than caching, which is what lets the retry pick up a
		// fresh version instead of repeating the same stale report.
		final var readOnlyWorker = new AbstractTaskWorker(processReportService, failureHandler) {
			@Override
			protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
				final var errand = supportManagementClient.getErrand(getMunicipalityId(externalTask), getNamespace(externalTask), getErrandId(externalTask)).getBody();
				return ProcessStateReport.completed().withErrandVersion(errand.getVersion());
			}
		};

		// First attempt: reads version 7, the report carrying it is rejected as stale
		readOnlyWorker.execute(task, taskService);

		verify(taskService).handleFailure(nullable(String.class), any(), any(), anyInt(), anyLong());
		verify(taskService, never()).complete(any(), any());

		// Second attempt, standing in for the engine's retry: rereads the errand and picks up its current version
		readOnlyWorker.execute(task, taskService);

		verify(taskService).complete(any(), any());
		verify(exactly(2), getRequestedFor(urlEqualTo(ERRAND_PATH)));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH)).withRequestBody(matchingJsonPath("$.errandVersion", equalTo("8"))));
	}

	@Test
	void executeBusinessLogicThrowingReportsRetryingAndDoesNotComplete() {
		mockApiGatewayToken();
		mockReportAccepted();

		final var task = mockTask();
		final var taskService = mock(ExternalTaskService.class);

		final var throwingWorker = new AbstractTaskWorker(processReportService, failureHandler) {
			@Override
			protected ProcessStateReport executeBusinessLogic(final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
				throw new IllegalStateException("Boom");
			}
		};

		throwingWorker.execute(task, taskService);

		// RUNNING went out before the throw, RETRYING went out from FailureHandler.reportFailure after it
		verify(exactly(2), putRequestedFor(urlEqualTo(REPORT_PATH)));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH)).withRequestBody(matchingJsonPath("$.processStatus", equalTo("RUNNING"))));
		verify(putRequestedFor(urlEqualTo(REPORT_PATH))
			.withRequestBody(matchingJsonPath("$.processStatus", equalTo("RETRYING")))
			.withRequestBody(matchingJsonPath("$.error.message", equalTo("Boom"))));
		verify(taskService).handleFailure(nullable(String.class), eq("Boom"), any(), anyInt(), anyLong());
		verify(taskService, never()).complete(any(), any());
	}
}
