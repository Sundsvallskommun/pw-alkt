package apptest;

import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import generated.se.sundsvall.operaton.HistoricProcessInstanceDto;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.dept44.test.AbstractAppTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static generated.se.sundsvall.operaton.HistoricProcessInstanceDto.StateEnum.COMPLETED;
import static java.time.Duration.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.setDefaultPollDelay;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.awaitility.Awaitility.setDefaultTimeout;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.sundsvall.alkt.Constants.PROCESS_KEYS;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

/**
 * Base for the integration tests that run a process: points the application at a live Operaton container and carries
 * the helpers for driving and reading the engine.
 */
abstract class AbstractOperatonAppTest extends AbstractAppTest {

	protected static final int DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS = 30;
	protected static final String ERRAND_EVENTS_PATH = "/2281/ALKT/process/errand-events";

	private static final String TENANT_ID_ALKT = "ALKT";
	// The errand processes plus process-reconciliation, which is deployed on its own and has no key in PROCESS_KEYS
	private static final int EXPECTED_DEPLOYMENTS = PROCESS_KEYS.size() + 1;
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

	/*
	 * Pinned rather than :latest - an unpinned tag makes the build non-reproducible and lets an upstream release break
	 * CI without a change in this repository. Bump deliberately.
	 */
	private static final String OPERATON_IMAGE = "operaton/operaton:2.1.3";

	/*
	 * One engine for the whole JVM: the field is static, so the image starts at most once per test run however many
	 * subclasses there are. Testcontainers' Ryuk reaps it on JVM exit - there is deliberately no teardown, so the same
	 * engine is reused by every test class. "/" answers 302, so the wait is on the REST endpoint.
	 */
	@SuppressWarnings("resource")
	private static final GenericContainer<?> OPERATON = new GenericContainer<>(OPERATON_IMAGE)
		.waitingFor(Wait.forHttp("/engine-rest/engine").forStatusCode(200))
		.withExposedPorts(8080);

	@Autowired
	protected OperatonClient operatonClient;

	/**
	 * Both properties target the same container: the external task client poll URL and the Operaton Feign client the
	 * test helpers read history through, so the poll path and the write path cannot drift apart.
	 */
	@DynamicPropertySource
	static synchronized void engine(final DynamicPropertyRegistry registry) {
		if (!OPERATON.isRunning()) {
			OPERATON.start();
		}
		final var baseUrl = "http://localhost:" + OPERATON.getMappedPort(8080) + "/engine-rest";

		registry.add("integration.operaton.url", () -> baseUrl);
		registry.add("camunda.bpm.client.base-url", () -> baseUrl);
	}

	/** Every process test waits out the same deployment before it can start anything. */
	@BeforeEach
	void awaitDeployments() {
		setDefaultPollInterval(500, MILLISECONDS);
		setDefaultPollDelay(ZERO);
		setDefaultTimeout(Duration.ofSeconds(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS));

		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.getDeployments(null, null, TENANT_ID_ALKT).size(), equalTo(EXPECTED_DEPLOYMENTS));
	}

	/** The engine outlives every test class, so whatever the previous one left running is cleared before the next. */
	@BeforeEach
	void resetSharedEngineState() {
		operatonClient.findProcessInstances(null, null, TENANT_ID_ALKT)
			.forEach(instance -> operatonClient.deleteProcessInstance(instance.getId(), false));

		wiremock.resetRequests();
	}

	protected List<HistoricActivityInstanceDto> getProcessInstanceRoute(String processInstanceId) {
		return getRoute(processInstanceId, new ArrayList<>());
	}

	/** The route up to where the cancellation landed, followed by the cancellation, which every model ends the same way. */
	protected void assertCancelledRoute(String processInstanceId, Tuple... routeBeforeCancellation) {
		assertThat(getProcessInstanceRoute(processInstanceId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrderElementsOf(concat(Stream.of(routeBeforeCancellation), Stream.of(
				tuple("Cancellation", "cancel_process_subprocess"),
				tuple("Process cancelled", "cancel_process"),
				tuple("Cancel process", "external_task_cancel_process"),
				tuple("End cancelled process", "end_process_cancelled"))).toList());
	}

	private List<HistoricActivityInstanceDto> getRoute(String processInstanceId, List<HistoricActivityInstanceDto> route) {
		if (isNull(processInstanceId)) {
			return route;
		}
		return operatonClient.getHistoricActivities(processInstanceId).stream()
			.filter(e -> e.getEndTime() != null)
			.sorted(comparing(HistoricActivityInstanceDto::getEndTime))
			.flatMap(activity -> concat(Stream.of(activity), getRoute(activity.getCalledProcessInstanceId(), route).stream()))
			.toList();
	}

	protected void awaitProcessCompleted(String processId, long timeoutInSeconds) {
		await()
			.ignoreExceptions()
			.atMost(timeoutInSeconds, SECONDS)
			.failFast("Wiremock has mismatch!", () -> !wiremock.findNearMissesForUnmatchedRequests().getNearMisses().isEmpty())
			.until(() -> operatonClient.getHistoricProcessInstance(processId).map(HistoricProcessInstanceDto::getState).orElse(null), equalTo(COMPLETED));
	}

	/** A work step reports the wait state after it completes, so the subscription can be there before the report. */
	protected void awaitReportAt(String activityId) {
		await()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> !wiremock.findAll(putRequestedFor(urlPathMatching(".*/processes/.*"))
				.withRequestBody(matchingJsonPath("$[?(@.currentActivityId == '%s')]".formatted(activityId)))).isEmpty());
	}

	protected void awaitProcessState(String processInstanceId, String state, long timeoutInSeconds) {
		await()
			.ignoreExceptions()
			.atMost(timeoutInSeconds, SECONDS)
			.failFast("Wiremock has mismatch!", () -> !wiremock.findNearMissesForUnmatchedRequests().getNearMisses().isEmpty())
			.until(() -> operatonClient.getEventSubscriptions(processInstanceId, null).stream().filter(eventSubscription -> state.equals(eventSubscription.getActivityId())).count(), equalTo(1L));
	}

	protected String awaitProcessInstance(String errandId, String processKey) {
		await()
			.ignoreExceptions()
			.atMost(DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS, SECONDS)
			.until(() -> operatonClient.findProcessInstances(errandId, processKey, TENANT_ID_ALKT).size(), equalTo(1));

		return operatonClient.findProcessInstances(errandId, processKey, TENANT_ID_ALKT).getFirst().getId();
	}

	protected void completePhase(String errandId, String processInstanceId, String processKey, String phase) {
		awaitProcessState(processInstanceId, "await_%s_completed".formatted(phase), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		sendSignal(errandId, processKey, "%s_completed".formatted(phase));
	}

	protected void cancelProcess(String errandId, String processInstanceId, String processKey, String phase) {
		awaitProcessState(processInstanceId, "await_%s_completed".formatted(phase), DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		sendSignal(errandId, processKey, "process_cancelled");
	}

	protected void sendSignal(String errandId, String processKey, String signalName) {
		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(JSON_MAPPER.writeValueAsString(signalEvent(errandId, processKey, signalName)))
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();
	}

	protected void completeDecision(String errandId, String processInstanceId, String processKey) {
		awaitProcessState(processInstanceId, "await_decision_updated", DEFAULT_TESTCASE_TIMEOUT_IN_SECONDS);

		setupCall()
			.withServicePath(ERRAND_EVENTS_PATH)
			.withHttpMethod(POST)
			.withRequest(JSON_MAPPER.writeValueAsString(decisionEvent(errandId, processKey)))
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseBodyIsNull()
			.sendRequest();
	}

	private static ErrandEvent decisionEvent(String errandId, String processKey) {
		return ErrandEvent.create()
			.withEventId(UUID.randomUUID().toString())
			.withEventType(UPDATE)
			.withEventSubType("DECISION")
			.withErrandId(errandId)
			.withProcessKey(processKey)
			.withStartAllowed(false);
	}

	private static ErrandEvent signalEvent(String errandId, String processKey, String signalName) {
		return ErrandEvent.create()
			.withEventId(UUID.randomUUID().toString())
			.withEventType(UPDATE)
			.withEventSubType("SIGNAL")
			.withErrandId(errandId)
			.withProcessKey(processKey)
			.withStartAllowed(false)
			.withSignalName(signalName);
	}
}
