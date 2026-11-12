package se.sundsvall.alkt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.BPMN_NAMESPACE;

/** The integration tests suspend the timers, so the schedule of the sweeps is only checked here. */
class ReconciliationModelTest {

	@Test
	void theNightlyModelStartsAtTwoMinutesPastThreeEveryNight() throws Exception {
		final var model = BpmnModel.read("processmodels/reconciliation/process-reconciliation-nightly.bpmn");

		assertThat(model.element(model.processId()).getElementsByTagNameNS(BPMN_NAMESPACE, "timeCycle").item(0).getTextContent().trim())
			.isEqualTo("0 2 3 * * ?");
	}

	@Test
	void theFiveMinuteModelStartsEveryFiveMinutes() throws Exception {
		final var model = BpmnModel.read("processmodels/reconciliation/process-reconciliation.bpmn");

		assertThat(model.element(model.processId()).getElementsByTagNameNS(BPMN_NAMESPACE, "timeCycle").item(0).getTextContent().trim())
			.isEqualTo("0 0/5 * * * ?");
	}
}
