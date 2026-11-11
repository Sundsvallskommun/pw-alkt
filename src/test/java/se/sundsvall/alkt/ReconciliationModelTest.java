package se.sundsvall.alkt;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.BPMN_NAMESPACE;
import static se.sundsvall.alkt.BpmnModel.CAMUNDA_NAMESPACE;

/** The engine starts both sweeps from their timers, on the topic of the one worker. */
class ReconciliationModelTest {

	private static final String NIGHTLY_MODEL = "processmodels/reconciliation/process-reconciliation-nightly.bpmn";
	private static final String FIVE_MINUTE_MODEL = "processmodels/reconciliation/process-reconciliation.bpmn";

	@Test
	void bothModelsLieInTheDeployedReconciliationFolder() throws Exception {
		assertThat(BpmnModel.modelsIn("reconciliation")).containsExactlyInAnyOrder(FIVE_MINUTE_MODEL, NIGHTLY_MODEL);
	}

	@Test
	void theNightlyModelStartsAtTwoMinutesPastThreeEveryNight() throws Exception {
		final var model = BpmnModel.read(NIGHTLY_MODEL);

		assertThat(model.processId()).isEqualTo("process-reconciliation-nightly");
		assertThat(timeCyclesOf(model)).containsExactly("0 2 3 * * ?");
	}

	@Test
	void theNightlyModelSweepsOnTheTopicOfTheWorker() throws Exception {
		final var model = BpmnModel.read(NIGHTLY_MODEL);

		assertThat(serviceTaskTopicsOf(model)).containsExactly("ReconcileProcessesTask");
	}

	@Test
	void theNightlyModelKeepsItsHistoryForADay() throws Exception {
		final var process = (Element) BpmnModel.read(NIGHTLY_MODEL).element("process-reconciliation-nightly");

		assertThat(process.getAttribute("isExecutable")).isEqualTo("true");
		assertThat(process.getAttributeNS(CAMUNDA_NAMESPACE, "historyTimeToLive")).isEqualTo("P1D");
	}

	@Test
	void theFiveMinuteModelIsUnchanged() throws Exception {
		final var model = BpmnModel.read(FIVE_MINUTE_MODEL);

		assertThat(model.processId()).isEqualTo("process-reconciliation");
		assertThat(timeCyclesOf(model)).containsExactly("0 0/5 * * * ?");
		assertThat(serviceTaskTopicsOf(model)).containsExactly("ReconcileProcessesTask");
	}

	private static List<String> timeCyclesOf(final BpmnModel model) {
		final var process = model.element(model.processId());
		return BpmnModel.children(process, "startEvent").stream()
			.map(start -> start.getElementsByTagNameNS(BPMN_NAMESPACE, "timeCycle"))
			.map(cycles -> cycles.getLength() == 0 ? "no timer" : cycles.item(0).getTextContent().trim())
			.toList();
	}

	private static List<String> serviceTaskTopicsOf(final BpmnModel model) {
		final var process = model.element(model.processId());
		return BpmnModel.children(process, "serviceTask").stream()
			.map(task -> task.getAttributeNS(CAMUNDA_NAMESPACE, "topic"))
			.toList();
	}
}
