package apptest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import generated.se.sundsvall.operaton.HistoricActivityInstanceDto;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import se.sundsvall.alkt.integration.operaton.OperatonClient;
import se.sundsvall.dept44.test.AbstractAppTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static generated.se.sundsvall.operaton.HistoricProcessInstanceDto.StateEnum.COMPLETED;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Base for the testcontainer-driven process tests. Holds the shared assertion and await helpers; the base class in the
 * {@code apptest.operaton} package points the application at the engine container by delegating its
 * {@code @DynamicPropertySource} to {@link apptest.engine.EngineTestProperties}. The {@code operatonClient} is used
 * purely as a read client for process history.
 *
 * @see Operaton API for more details https://docs.operaton.org/rest/
 */
public abstract class AbstractEngineAppTest extends AbstractAppTest {

	private static final String TENANT_ID_ALKT = "ALKT";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private final Logger logger;

	@Autowired
	protected OperatonClient operatonClient;

	@Value("${integration.operaton.url}")
	private String engineBaseUrl;

	protected AbstractEngineAppTest() {
		this.logger = LoggerFactory.getLogger(getClass());
	}

	/**
	 * Gives every test a clean slate on the shared engine container before it runs. The container is reused across
	 * {@code @DirtiesContext} contexts, so a process instance that outlived an earlier test (after a failure, or because
	 * the engine had not drained it yet) would otherwise be picked up by this context's external task workers and corrupt
	 * this test's WireMock scenario state. We delete the tenant's instances and then clear the WireMock request journal,
	 * so a stray request from a leftover worker racing the context startup cannot trip the {@code failFast} near-miss
	 * check before the test's own flow even begins.
	 */
	@BeforeEach
	void resetSharedEngineState() throws Exception {
		final var listRequest = HttpRequest.newBuilder(URI.create(engineBaseUrl + "/process-instance?tenantIdIn=" + TENANT_ID_ALKT)).GET().build();
		final var listResponse = HTTP_CLIENT.send(listRequest, HttpResponse.BodyHandlers.ofString());
		final JsonNode instances = OBJECT_MAPPER.readTree(listResponse.body());
		// An error response is a JSON object, not an array - iterating it would yield field values and NPE below,
		// hiding the actual engine error behind a stack trace from this @BeforeEach.
		if (!instances.isArray()) {
			throw new IllegalStateException("Unexpected response when listing process instances (HTTP " + listResponse.statusCode() + "): " + listResponse.body());
		}
		for (final JsonNode instance : instances) {
			final var deleteRequest = HttpRequest.newBuilder(
				URI.create(engineBaseUrl + "/process-instance/" + instance.get("id").asText() + "?skipCustomListeners=true&skipIoMappings=true&failIfNotExists=false"))
				.DELETE().build();
			HTTP_CLIENT.send(deleteRequest, HttpResponse.BodyHandlers.discarding());
		}
		wiremock.resetRequests();
	}

	protected List<HistoricActivityInstanceDto> getProcessInstanceRoute(String processInstanceId) {
		return getRoute(processInstanceId, new ArrayList<>());
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
			.until(() -> operatonClient.getHistoricProcessInstance(processId).getState(), equalTo(COMPLETED));
	}

	protected void awaitProcessState(String state, long timeoutInSeconds) {
		await()
			.ignoreExceptions()
			.atMost(timeoutInSeconds, SECONDS)
			.failFast("Wiremock has mismatch!", () -> !wiremock.findNearMissesForUnmatchedRequests().getNearMisses().isEmpty())
			.until(() -> operatonClient.getEventSubscriptions().stream().filter(eventSubscription -> state.equals(eventSubscription.getActivityId())).count(), equalTo(1L));
	}

	protected void assertProcessPathway(String processId, boolean acceptDuplication, ArrayList<Tuple> list) {
		final var element = assertThat(getProcessInstanceRoute(processId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrderElementsOf(list);
		if (!acceptDuplication) {
			element.doesNotHaveDuplicates();
		}
	}

	protected void logMockInformation() {
		final var fixedColumnWidthFormat = "%-100s"; // Fixed 100 char long colum width

		wiremock.getAllScenarios().getScenarios().stream().forEach(scenario -> {
			logger.info("Scenario:" + scenario.getName());

			logger.info(String.format(fixedColumnWidthFormat, "[From state]") + String.format(fixedColumnWidthFormat, "[To state]") + "[Url match]");

			wiremock.getStubMappings().stream()
				.sorted(reverseOrder((stub1, stub2) -> Long.compare(stub2.getInsertionIndex(), stub1.getInsertionIndex()))) // Reverse to get start of flow at top
				.forEach(mapping -> logger.info(String.format(fixedColumnWidthFormat, mapping.getRequiredScenarioState()) +
					String.format(fixedColumnWidthFormat, mapping.getNewScenarioState()) +
					mapping.getRequest().getMethod() + " " + mapping.getRequest().getUrl() + " " + mapping.getRequest().getBodyPatterns()));
		});
	}
}
