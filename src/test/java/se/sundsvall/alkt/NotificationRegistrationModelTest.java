package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A notification from the e-service passes the registration on its own. One a case worker created waits for
 * registration_completed. The submission PDF step tells the two apart: it is skipped for an errand not from the
 * e-service. Both end with the step that tells whether the review needs a case number.
 */
class NotificationRegistrationModelTest {

	private static final String STEP_ID = "external_task_create_submission_pdf";
	private static final String GATE_ID = "await_registration_completed";
	private static final String REQUIRED_ID = "external_task_check_case_number_required";
	private static final String END_ID = "end_registration_phase";

	static List<String> models() {
		return List.of(
			"processmodels/notification/catering-occasion.bpmn",
			"processmodels/notification/e-cigarette-sales.bpmn",
			"processmodels/notification/low-alcohol-beer-sales.bpmn",
			"processmodels/notification/low-alcohol-beer-serving.bpmn",
			"processmodels/notification/low-alcohol-beer-sales-and-serving.bpmn",
			"processmodels/notification/tobacco-free-nicotine-product-sales.bpmn");
	}

	@Test
	void everyNotificationModelIsCovered() throws Exception {
		assertThat(BpmnModel.modelsIn("notification")).containsExactlyInAnyOrderElementsOf(models());
	}

	@ParameterizedTest
	@MethodSource("models")
	void aManuallyCreatedErrandWaitsForRegistrationCompletedAndThenChecksWhetherACaseNumberIsRequired(final String path) throws Exception {
		final var model = BpmnModel.read(path);
		final var skipped = model.boundaryOf(STEP_ID).getAttribute("id");

		assertThat(model.targetsFrom(skipped)).containsExactly(GATE_ID);
		assertThat(model.typeOf(GATE_ID)).isEqualTo("intermediateCatchEvent");
		assertThat(model.parentIdOf(GATE_ID)).isEqualTo("registration_phase");
		assertThat(model.messageNameOf(GATE_ID)).isEqualTo("registration_completed");
		assertThat(model.targetsFrom(GATE_ID)).containsExactly(REQUIRED_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void anErrandFromTheEServiceChecksWhetherACaseNumberIsRequiredWithoutTheGate(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.targetsFrom(STEP_ID)).containsExactly(REQUIRED_ID);
		assertThat(model.messageCatchEventsIn("registration_phase")).containsExactly(GATE_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void theCheckWhetherACaseNumberIsRequiredEndsTheRegistration(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.typeOf(REQUIRED_ID)).isEqualTo("serviceTask");
		assertThat(model.topicOf(REQUIRED_ID)).isEqualTo("CheckCaseNumberRequiredTask");
		assertThat(model.targetsFrom(REQUIRED_ID)).containsExactly(END_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingInTheRegistrationIsAsynchronous(final String path) throws Exception {
		assertThat(BpmnModel.read(path).asynchronousElementsIn("registration_phase")).isEmpty();
	}
}
