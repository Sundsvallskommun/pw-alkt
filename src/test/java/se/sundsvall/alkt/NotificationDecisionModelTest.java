package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/** Once the asset is created, the customer is told that the notification is registered, and the decision ends. */
class NotificationDecisionModelTest {

	private static final String ASSET_ID = "external_task_create_asset";
	private static final String NOTIFY_ID = "external_task_notify_notification_registered";
	private static final String END_ID = "end_decision_phase";

	static List<String> models() {
		return NotificationRegistrationModelTest.models();
	}

	@ParameterizedTest
	@MethodSource("models")
	void theRegisteredMessageFollowsTheAssetAndEndsTheDecision(final String path) throws Exception {
		final var model = BpmnModel.read(path);

		assertThat(model.targetsFrom(ASSET_ID)).containsExactly(NOTIFY_ID);
		assertThat(model.typeOf(NOTIFY_ID)).isEqualTo("serviceTask");
		assertThat(model.parentIdOf(NOTIFY_ID)).isEqualTo("decision_phase");
		assertThat(model.element(NOTIFY_ID).getAttributeNS(BpmnModel.CAMUNDA_NAMESPACE, "type")).isEqualTo("external");
		assertThat(model.topicOf(NOTIFY_ID)).isEqualTo("NotifyCustomerTask");
		assertThat(model.inputParameterOf(NOTIFY_ID, "message")).isEqualTo("notification-registered");
		assertThat(model.targetsFrom(NOTIFY_ID)).containsExactly(END_ID);
		assertThat(model.typeOf(END_ID)).isEqualTo("endEvent");
	}

	@ParameterizedTest
	@MethodSource("models")
	void aSkippedRegisteredMessageEndsTheDecision(final String path) throws Exception {
		final var model = BpmnModel.read(path);
		final var boundary = model.boundaryOf(NOTIFY_ID);
		final var errorRef = ((Element) boundary.getElementsByTagNameNS(BpmnModel.BPMN_NAMESPACE, "errorEventDefinition").item(0)).getAttribute("errorRef");

		assertThat(errorRef).isEqualTo("error_step_skipped");
		assertThat(model.element(errorRef).getAttribute("errorCode")).isEqualTo("step_skipped");
		assertThat(model.targetsFrom(boundary.getAttribute("id"))).containsExactly(END_ID);
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingInTheDecisionIsAsynchronous(final String path) throws Exception {
		assertThat(BpmnModel.read(path).asynchronousElementsIn("decision_phase")).isEmpty();
	}
}
