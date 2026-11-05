package se.sundsvall.alkt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.alkt.service.model.ProcessType;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.BPMN_NAMESPACE;
import static se.sundsvall.alkt.Constants.ACTION_ERRAND_DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES_CREATING_ASSET;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;

/**
 * The outcomes live both in Constants and in the conditions of the gateway, and an outcome the gateway does not name
 * leaves the process with no way on. Each flow must name exactly the outcomes Constants gives it.
 */
class DecisionOutcomeGatewayTest {

	private static final Pattern QUOTED_OUTCOME = Pattern.compile("'([A-Z_]+)'");
	private static final String ACTION_ERRAND_MODEL = "processmodels/inspection/action-errand.bpmn";
	private static final String GATEWAY = "gateway_decision_outcome";

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
		final var models = new HashSet<String>();
		for (final var model : BpmnModel.allModels()) {
			if (BpmnModel.read(model).hasServiceTaskOfTopic("CheckDecisionTask")) {
				models.add(model);
			}
		}
		return models.stream();
	}

	@ParameterizedTest
	@MethodSource("modelsCheckingTheDecision")
	void theServiceKnowsExactlyTheOutcomesOfTheGateway(final String path) throws Exception {
		final var model = BpmnModel.read(path);
		final var processType = ProcessType.ofKey(model.processId());
		final var gatewayOutcomes = model.targetsFrom(GATEWAY).stream()
			.flatMap(target -> outcomesIn(model.conditionOf(GATEWAY, target)).stream())
			.collect(Collectors.toSet());

		Stream.of(DECISION_OUTCOMES, ACTION_ERRAND_DECISION_OUTCOMES, gatewayOutcomes)
			.flatMap(Collection::stream)
			.distinct()
			.forEach(outcome -> assertThat(processType.isKnownOutcome(outcome))
				.as("%s knows %s", processType.getKey(), outcome)
				.isEqualTo(gatewayOutcomes.contains(outcome)));
	}

	private static Set<String> outcomesOf(final String model, final String flowId) throws Exception {
		return outcomesIn(BpmnModel.read(model).element(flowId).getElementsByTagNameNS(BPMN_NAMESPACE, "conditionExpression").item(0).getTextContent());
	}

	private static Set<String> outcomesIn(final String condition) {
		return QUOTED_OUTCOME.matcher(condition).results()
			.map(result -> result.group(1))
			.collect(Collectors.toSet());
	}
}
