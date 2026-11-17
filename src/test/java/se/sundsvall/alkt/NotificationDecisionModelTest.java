package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.BPMN_NAMESPACE;

/** A registered message that cannot be sent is skipped, and the decision ends. */
class NotificationDecisionModelTest {

	private static final String NOTIFY_ID = "external_task_notify_notification_registered";
	private static final String END_ID = "end_decision_phase";

	static List<String> models() {
		return NotificationRegistrationModelTest.models();
	}

	@ParameterizedTest
	@MethodSource("models")
	void aSkippedRegisteredMessageEndsTheDecision(final String path) throws Exception {
		final var model = BpmnModel.read(path);
		final var boundary = model.boundaryOf(NOTIFY_ID);
		final var errorRef = ((Element) boundary.getElementsByTagNameNS(BPMN_NAMESPACE, "errorEventDefinition").item(0)).getAttribute("errorRef");

		assertThat(errorRef).isEqualTo("error_step_skipped");
		assertThat(model.element(errorRef).getAttribute("errorCode")).isEqualTo("step_skipped");
		assertThat(model.targetsFrom(boundary.getAttribute("id"))).containsExactly(END_ID);
	}
}
