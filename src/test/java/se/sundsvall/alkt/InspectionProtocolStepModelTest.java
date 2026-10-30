package se.sundsvall.alkt;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.CAMUNDA_NAMESPACE;

class InspectionProtocolStepModelTest {

	private static final String STEP_ID = "external_task_create_protocol";

	@ParameterizedTest
	@CsvSource({
		"processmodels/inspection/internal-inspection.bpmn, internal-inspection-protocol",
		"processmodels/inspection/external-inspection.bpmn, serving-inspection-protocol"
	})
	void theStepNamesTheTemplateAndNoFileName(final String path, final String template) throws Exception {
		final var inputs = BpmnModel.read(path).element(STEP_ID).getElementsByTagNameNS(CAMUNDA_NAMESPACE, "inputParameter");
		final var inputParameters = new ArrayList<List<String>>();
		for (var i = 0; i < inputs.getLength(); i++) {
			final var input = (Element) inputs.item(i);
			inputParameters.add(List.of(input.getAttribute("name"), input.getTextContent().trim()));
		}

		assertThat(inputParameters).containsExactly(List.of("protocolTemplate", template));
	}
}
