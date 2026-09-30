package se.sundsvall.alkt;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

	private static final String MODEL = "processmodels/application/alcohol-serving.bpmn";
	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final Pattern QUOTED_OUTCOME = Pattern.compile("'([A-Z_]+)'");

	private static Document model;

	@BeforeAll
	static void readModel() throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setNamespaceAware(true);
		try (final InputStream xml = DecisionOutcomeGatewayTest.class.getClassLoader().getResourceAsStream(MODEL)) {
			model = factory.newDocumentBuilder().parse(xml);
		}
	}

	@Test
	void theWayToThePermitNamesTheOutcomesThatCreateIt() {
		assertThat(outcomesOf("flow_decision_gateway_to_create_asset")).isEqualTo(DECISION_OUTCOMES_CREATING_ASSET);
	}

	@Test
	void theWayPastThePermitNamesEveryOtherOutcome() {
		final var noPermit = new HashSet<>(DECISION_OUTCOMES);
		noPermit.removeAll(DECISION_OUTCOMES_CREATING_ASSET);

		assertThat(outcomesOf("flow_decision_gateway_to_no_permit")).isEqualTo(noPermit);
	}

	@Test
	void theWayBackToWaitingNamesNoOutcome() {
		assertThat(outcomesOf("flow_decision_gateway_to_wait")).containsExactly(DECISION_OUTCOME_NONE);
	}

	private static Set<String> outcomesOf(final String flowId) {
		final var flows = model.getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");
		for (var i = 0; i < flows.getLength(); i++) {
			final var flow = (Element) flows.item(i);
			if (flowId.equals(flow.getAttribute("id"))) {
				final var condition = flow.getElementsByTagNameNS(BPMN_NAMESPACE, "conditionExpression").item(0).getTextContent();
				return QUOTED_OUTCOME.matcher(condition).results()
					.map(result -> result.group(1))
					.collect(Collectors.toSet());
			}
		}
		throw new AssertionError("No sequence flow '%s' in %s".formatted(flowId, MODEL));
	}
}
