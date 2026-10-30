package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A notification that needs no case number passes the review on its own. One that needs a case number waits for
 * review_completed and goes on only once the errand has one.
 */
class NotificationReviewModelTest {

	private static final String REQUIRED_GATEWAY_ID = "gateway_case_number_required";
	private static final String GATE_ID = "await_review_completed";
	private static final String CHECK_ID = "external_task_check_case_number";
	private static final String CHECK_GATEWAY_ID = "gateway_case_number_accepted";
	private static final String END_ID = "end_review_phase";

	static List<String> models() {
		return NotificationRegistrationModelTest.models();
	}

	@ParameterizedTest
	@MethodSource("models")
	void theReviewOpensWithTheGatewayAndNoStep(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.targetsFrom("start_review_phase")).containsExactly(REQUIRED_GATEWAY_ID);
		assertThat(model.typeOf(REQUIRED_GATEWAY_ID)).isEqualTo("exclusiveGateway");
	}

	@ParameterizedTest
	@MethodSource("models")
	void withoutARequiredCaseNumberTheReviewEndsWithoutTheGate(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.targetsFrom(REQUIRED_GATEWAY_ID)).containsExactlyInAnyOrder(END_ID, GATE_ID);
		assertThat(model.conditionOf(REQUIRED_GATEWAY_ID, END_ID)).isEqualTo("${!caseNumberRequired}");
	}

	@ParameterizedTest
	@MethodSource("models")
	void withARequiredCaseNumberTheReviewWaitsForReviewCompletedAndThenChecksTheCaseNumber(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.conditionOf(REQUIRED_GATEWAY_ID, GATE_ID)).isEqualTo("${caseNumberRequired}");
		assertThat(model.typeOf(GATE_ID)).isEqualTo("intermediateCatchEvent");
		assertThat(model.parentIdOf(GATE_ID)).isEqualTo("review_phase");
		assertThat(model.messageNameOf(GATE_ID)).isEqualTo("review_completed");
		assertThat(model.targetsFrom(GATE_ID)).containsExactly(CHECK_ID);
		assertThat(model.typeOf(CHECK_ID)).isEqualTo("serviceTask");
		assertThat(model.topicOf(CHECK_ID)).isEqualTo("CheckCaseNumberTask");
		assertThat(model.targetsFrom(CHECK_ID)).containsExactly(CHECK_GATEWAY_ID);
		assertThat(model.conditionOf(CHECK_GATEWAY_ID, END_ID)).isEqualTo("${caseNumberAccepted}");
	}

	@ParameterizedTest
	@MethodSource("models")
	void aRejectedCheckGoesBackToTheGate(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.targetsFrom(CHECK_GATEWAY_ID)).containsExactlyInAnyOrder(END_ID, GATE_ID);
		assertThat(model.conditionOf(CHECK_GATEWAY_ID, GATE_ID)).isEqualTo("${!caseNumberAccepted}");
		assertThat(model.messageCatchEventsIn("review_phase")).containsExactly(GATE_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void theInvestigationAndTheDecisionHaveNoGate(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.messageCatchEventsIn("investigation_phase")).isEmpty();
		assertThat(model.messageCatchEventsIn("decision_phase")).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingFromTheReviewToTheFollowUpIsAsynchronous(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.asynchronousElementsIn("review_phase")).isEmpty();
		assertThat(model.asynchronousElementsIn("investigation_phase")).isEmpty();
		assertThat(model.asynchronousElementsIn("decision_phase")).isEmpty();
	}
}
