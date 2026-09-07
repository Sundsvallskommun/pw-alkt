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

public abstract class AbstractEngineAppTest extends AbstractAppTest {

	private static final String TENANT_ID_ALKT = "ALKT";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Autowired
	protected OperatonClient operatonClient;

	@Value("${integration.operaton.url}")
	private String engineBaseUrl;

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

	protected void awaitProcessState(String processInstanceId, String state, long timeoutInSeconds) {
		await()
			.ignoreExceptions()
			.atMost(timeoutInSeconds, SECONDS)
			.failFast("Wiremock has mismatch!", () -> !wiremock.findNearMissesForUnmatchedRequests().getNearMisses().isEmpty())
			.until(() -> operatonClient.getEventSubscriptions(processInstanceId, null).stream().filter(eventSubscription -> state.equals(eventSubscription.getActivityId())).count(), equalTo(1L));
	}

	protected void assertProcessPathway(String processId, boolean acceptDuplication, ArrayList<Tuple> list) {
		final var element = assertThat(getProcessInstanceRoute(processId))
			.extracting(HistoricActivityInstanceDto::getActivityName, HistoricActivityInstanceDto::getActivityId)
			.containsExactlyInAnyOrderElementsOf(list);
		if (!acceptDuplication) {
			element.doesNotHaveDuplicates();
		}
	}
}
