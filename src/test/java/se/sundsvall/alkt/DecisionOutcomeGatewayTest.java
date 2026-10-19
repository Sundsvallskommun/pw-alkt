package se.sundsvall.alkt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.sundsvall.alkt.service.model.ProcessType;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.Constants.ACTION_ERRAND_DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;

/**
 * The outcomes live both in Constants and in the conditions of the gateway, and an outcome the gateway does not name
 * leaves the process with no way on. Each flow must name exactly the outcomes Constants gives it.
 */
class DecisionOutcomeGatewayTest {

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String CAMUNDA_NAMESPACE = "http://camunda.org/schema/1.0/bpmn";
	private static final Pattern QUOTED_OUTCOME = Pattern.compile("'([A-Z_]+)'");
	private static final String ACTION_ERRAND_MODEL = "processmodels/inspection/action-errand.bpmn";

	private static Stream<Arguments> models() {
		return Stream.of(
			Arguments.of("processmodels/application/alcohol-serving.bpmn", "flow_decision_gateway_to_resolve_restaurant_number"),
			Arguments.of("processmodels/application/alcohol-serving-addition.bpmn", "flow_decision_gateway_to_find_restaurant_number"),
			Arguments.of("processmodels/application/alcohol-serving-change.bpmn", "flow_decision_gateway_to_update_asset"),
			Arguments.of("processmodels/application/tobacco-sales.bpmn", "flow_decision_gateway_to_create_asset"),
			Arguments.of("processmodels/application/tobacco-sales-change.bpmn", "flow_decision_gateway_to_update_asset"),
			Arguments.of("processmodels/application/tobacco-sales-closure.bpmn", "flow_decision_gateway_to_close_asset"));
	}

	private static Stream<String> modelPaths() {
		return models().map(arguments -> (String) arguments.get()[0]);
	}

	@ParameterizedTest
	@MethodSource("models")
	void theWayToThePermitNamesTheOutcomesThatGrantIt(final String model, final String flowToPermit) throws Exception {
		assertThat(outcomesOf(model, flowToPermit)).isEqualTo(DECISION_OUTCOMES_CREATING_ASSET);
	}

	@ParameterizedTest
	@MethodSource("modelPaths")
	void theWayPastThePermitNamesEveryOtherOutcome(final String model) throws Exception {
		final var noPermit = new HashSet<>(DECISION_OUTCOMES);
		noPermit.removeAll(DECISION_OUTCOMES_CREATING_ASSET);

		assertThat(outcomesOf(model, "flow_decision_gateway_to_no_permit")).isEqualTo(noPermit);
	}

	@ParameterizedTest
	@MethodSource("modelPaths")
	void theWayBackToWaitingNamesNoOutcome(final String model) throws Exception {
		assertThat(outcomesOf(model, "flow_decision_gateway_to_wait")).containsExactly(DECISION_OUTCOME_NONE);
	}

	@Test
	void theActionErrandNamesItsOwnOutcomes() throws Exception {
		assertThat(outcomesOf(ACTION_ERRAND_MODEL, "flow_decision_gateway_to_decided")).isEqualTo(ACTION_ERRAND_DECISION_OUTCOMES);
		assertThat(outcomesOf(ACTION_ERRAND_MODEL, "flow_decision_gateway_to_wait")).containsExactly(DECISION_OUTCOME_NONE);
	}

	/** Found by the topic, so a new model that reads a decision cannot be left out. */
	private static Stream<String> modelsCheckingTheDecision() throws Exception {
		return Stream.of(new PathMatchingResourcePatternResolver().getResources("classpath*:processmodels/*/*.bpmn"))
			.map(DecisionOutcomeGatewayTest::classpathOf)
			.filter(DecisionOutcomeGatewayTest::hasCheckDecisionTask);
	}

	@ParameterizedTest
	@MethodSource("modelsCheckingTheDecision")
	void theServiceKnowsExactlyTheOutcomesOfTheGateway(final String model) throws Exception {
		final var document = read(model);
		final var processType = ProcessType.ofKey(((Element) document.getElementsByTagNameNS(BPMN_NAMESPACE, "process").item(0)).getAttribute("id"));
		final var gatewayOutcomes = outcomesLeaving(document, "gateway_decision_outcome");

		Stream.of(DECISION_OUTCOMES, ACTION_ERRAND_DECISION_OUTCOMES, gatewayOutcomes)
			.flatMap(Collection::stream)
			.distinct()
			.forEach(outcome -> assertThat(processType.isKnownOutcome(outcome))
				.as("%s knows %s", processType.getKey(), outcome)
				.isEqualTo(gatewayOutcomes.contains(outcome)));
	}

	private static String classpathOf(final Resource resource) {
		try {
			final var url = resource.getURL().toString();
			return url.substring(url.lastIndexOf("processmodels/"));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean hasCheckDecisionTask(final String model) {
		try {
			final var tasks = read(model).getElementsByTagNameNS(BPMN_NAMESPACE, "serviceTask");
			return IntStream.range(0, tasks.getLength())
				.mapToObj(i -> (Element) tasks.item(i))
				.anyMatch(task -> "CheckDecisionTask".equals(task.getAttributeNS(CAMUNDA_NAMESPACE, "topic")));
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Set<String> outcomesLeaving(final Document document, final String gatewayId) {
		final var flows = document.getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");
		return IntStream.range(0, flows.getLength())
			.mapToObj(i -> (Element) flows.item(i))
			.filter(flow -> gatewayId.equals(flow.getAttribute("sourceRef")))
			.flatMap(flow -> QUOTED_OUTCOME.matcher(flow.getElementsByTagNameNS(BPMN_NAMESPACE, "conditionExpression").item(0).getTextContent()).results())
			.map(result -> result.group(1))
			.collect(Collectors.toSet());
	}

	private static Set<String> outcomesOf(final String model, final String flowId) throws Exception {
		final var flows = read(model).getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");
		for (var i = 0; i < flows.getLength(); i++) {
			final var flow = (Element) flows.item(i);
			if (flowId.equals(flow.getAttribute("id"))) {
				final var condition = flow.getElementsByTagNameNS(BPMN_NAMESPACE, "conditionExpression").item(0).getTextContent();
				return QUOTED_OUTCOME.matcher(condition).results()
					.map(result -> result.group(1))
					.collect(Collectors.toSet());
			}
		}
		throw new AssertionError("No sequence flow '%s' in %s".formatted(flowId, model));
	}

	private static Document read(final String model) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setNamespaceAware(true);
		try (final InputStream xml = DecisionOutcomeGatewayTest.class.getClassLoader().getResourceAsStream(model)) {
			return factory.newDocumentBuilder().parse(xml);
		}
	}
}
