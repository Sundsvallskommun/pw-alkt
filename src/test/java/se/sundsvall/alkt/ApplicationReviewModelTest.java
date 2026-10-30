package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * After review_completed an application goes on to the investigation only once the case number check accepts it, which
 * in Sundsvall it always does. A refused check waits for review_completed again, without telling the customer twice.
 */
class ApplicationReviewModelTest {

	private static final String NOTIFY_ID = "external_task_notify_processing_started";
	private static final String GATE_ID = "await_review_completed";
	private static final String CHECK_ID = "external_task_check_case_number";
	private static final String CHECK_GATEWAY_ID = "gateway_case_number_accepted";
	private static final String END_ID = "end_review_phase";

	static List<String> models() {
		return List.of(
			"processmodels/application/alcohol-serving.bpmn",
			"processmodels/application/alcohol-serving-addition.bpmn",
			"processmodels/application/alcohol-serving-change.bpmn",
			"processmodels/application/tobacco-sales.bpmn",
			"processmodels/application/tobacco-sales-change.bpmn",
			"processmodels/application/tobacco-sales-closure.bpmn");
	}

	@Test
	void everyApplicationModelIsCovered() throws Exception {
		assertThat(BpmnModel.modelsIn("application")).containsExactlyInAnyOrderElementsOf(models());
	}

	@ParameterizedTest
	@MethodSource("models")
	void theCaseNumberIsCheckedAfterReviewCompletedAndAnAcceptedCheckEndsTheReview(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.messageNameOf(GATE_ID)).isEqualTo("review_completed");
		assertThat(model.targetsFrom(GATE_ID)).containsExactly(CHECK_ID);
		assertThat(model.typeOf(CHECK_ID)).isEqualTo("serviceTask");
		assertThat(model.parentIdOf(CHECK_ID)).isEqualTo("review_phase");
		assertThat(model.topicOf(CHECK_ID)).isEqualTo("CheckCaseNumberTask");
		assertThat(model.targetsFrom(CHECK_ID)).containsExactly(CHECK_GATEWAY_ID);
		assertThat(model.typeOf(CHECK_GATEWAY_ID)).isEqualTo("exclusiveGateway");
		assertThat(model.targetsFrom(CHECK_GATEWAY_ID)).containsExactlyInAnyOrder(END_ID, GATE_ID);
		assertThat(model.conditionOf(CHECK_GATEWAY_ID, END_ID)).isEqualTo("${caseNumberAccepted}");
	}

	@ParameterizedTest
	@MethodSource("models")
	void aRejectedCheckGoesBackToTheGateAndNotToTheCustomerMessage(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.conditionOf(CHECK_GATEWAY_ID, GATE_ID)).isEqualTo("${!caseNumberAccepted}");
		assertThat(model.targetsFrom("start_review_phase")).containsExactly(NOTIFY_ID);
		assertThat(model.targetsFrom(NOTIFY_ID)).containsExactly(GATE_ID);
		assertThat(model.messageCatchEventsIn("review_phase")).containsExactly(GATE_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingInTheReviewIsAsynchronous(final String path) throws Exception {
		assertThat(BpmnModel.read(path).asynchronousElementsIn("review_phase")).isEmpty();
	}
}
