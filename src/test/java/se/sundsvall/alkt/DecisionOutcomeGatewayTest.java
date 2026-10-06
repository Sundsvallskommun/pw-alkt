package se.sundsvall.alkt;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;

/**
 * The outcomes live both in Constants and in the conditions of the gateway, and an outcome the gateway does not name
 * leaves the process with no way on. Each flow must name exactly the outcomes Constants gives it.
 */
class DecisionOutcomeGatewayTest {

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final Pattern QUOTED_OUTCOME = Pattern.compile("'([A-Z_]+)'");

	private static Stream<Arguments> models() {
		return Stream.of(
			Arguments.of("processmodels/application/alcohol-serving.bpmn", "flow_decision_gateway_to_resolve_restaurant_number"),
			Arguments.of("processmodels/application/alcohol-serving-addition.bpmn", "flow_decision_gateway_to_find_restaurant_number"),
			Arguments.of("processmodels/application/alcohol-serving-change.bpmn", "flow_decision_gateway_to_update_asset"));
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
