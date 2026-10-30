package se.sundsvall.alkt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.BpmnModel.BPMN_NAMESPACE;
import static se.sundsvall.alkt.BpmnModel.CAMUNDA_NAMESPACE;

/** The submission PDF is the first step of the registration of every errand that came in through the form. */
class SubmissionPdfStepModelTest {

	private static final String STEP_ID = "external_task_create_submission_pdf";
	private static final String TOPIC = "CreateSubmissionPdfTask";

	/**
	 * Each model with where a done and a skipped step go on to. A notification a case worker created waits in the
	 * registration.
	 */
	private static Stream<Arguments> models() {
		return Stream.of(
			Arguments.of("processmodels/application/alcohol-serving.bpmn", "await_registration_completed", "await_registration_completed"),
			Arguments.of("processmodels/application/alcohol-serving-addition.bpmn", "await_registration_completed", "await_registration_completed"),
			Arguments.of("processmodels/application/alcohol-serving-change.bpmn", "external_task_create_change_draft", "external_task_create_change_draft"),
			Arguments.of("processmodels/application/tobacco-sales.bpmn", "await_registration_completed", "await_registration_completed"),
			Arguments.of("processmodels/application/tobacco-sales-change.bpmn", "external_task_create_change_draft", "external_task_create_change_draft"),
			Arguments.of("processmodels/application/tobacco-sales-closure.bpmn", "external_task_check_permit", "external_task_check_permit"),
			Arguments.of("processmodels/notification/catering-occasion.bpmn", "external_task_check_case_number_required", "await_registration_completed"),
			Arguments.of("processmodels/notification/e-cigarette-sales.bpmn", "external_task_check_case_number_required", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-sales.bpmn", "external_task_check_case_number_required", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-serving.bpmn", "external_task_check_case_number_required", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-sales-and-serving.bpmn", "external_task_check_case_number_required", "await_registration_completed"),
			Arguments.of("processmodels/notification/tobacco-free-nicotine-product-sales.bpmn", "external_task_check_case_number_required", "await_registration_completed"));
	}

	@Test
	void everyApplicationAndNotificationModelIsCovered() throws Exception {
		final var files = new ArrayList<String>();
		files.addAll(BpmnModel.modelsIn("application"));
		files.addAll(BpmnModel.modelsIn("notification"));

		assertThat(files).containsExactlyInAnyOrderElementsOf(models().map(arguments -> (String) arguments.get()[0]).toList());
	}

	@ParameterizedTest
	@MethodSource("models")
	void theStepIsTheFirstAfterTheStartOfTheRegistration(final String path, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var model = BpmnModel.read(path);
		final var start = BpmnModel.children(model.element("registration_phase"), "startEvent").getFirst();

		assertThat(model.targetsFrom(start.getAttribute("id"))).containsExactly(STEP_ID);
		assertThat(model.typeOf(STEP_ID)).isEqualTo("serviceTask");
		assertThat(model.parentIdOf(STEP_ID)).isEqualTo("registration_phase");
		assertThat(model.element(STEP_ID).getAttributeNS(CAMUNDA_NAMESPACE, "type")).isEqualTo("external");
		assertThat(model.topicOf(STEP_ID)).isEqualTo(TOPIC);
	}

	@ParameterizedTest
	@MethodSource("models")
	void aDoneAndASkippedStepGoOnToTheirNextElement(final String path, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var model = BpmnModel.read(path);
		final var boundary = model.boundaryOf(STEP_ID);
		final var errorRef = ((Element) boundary.getElementsByTagNameNS(BPMN_NAMESPACE, "errorEventDefinition").item(0)).getAttribute("errorRef");

		assertThat(model.targetsFrom(STEP_ID)).containsExactly(nextWhenDone);
		assertThat(model.targetsFrom(boundary.getAttribute("id"))).containsExactly(nextWhenSkipped);
		assertThat(model.element(errorRef).getAttribute("errorCode")).isEqualTo("step_skipped");
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingIsAsynchronousBetweenTheStepAndTheNextWaitState(final String path, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var model = BpmnModel.read(path);

		for (final var id : List.of(STEP_ID, nextWhenDone, nextWhenSkipped)) {
			final var element = model.element(id);
			assertThat(element.getAttributeNS(CAMUNDA_NAMESPACE, "asyncBefore")).as(id).isNotEqualTo("true");
			assertThat(element.getAttributeNS(CAMUNDA_NAMESPACE, "asyncAfter")).as(id).isNotEqualTo("true");
		}
	}

	@Test
	void noInspectionOrReconciliationModelHasTheStep() throws Exception {
		final var resolver = new PathMatchingResourcePatternResolver();
		for (final var folder : List.of("inspection", "reconciliation")) {
			final var resources = resolver.getResources("classpath*:processmodels/" + folder + "/*.bpmn");
			assertThat(resources).isNotEmpty();
			for (final Resource resource : resources) {
				final var content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
				assertThat(content).as(resource.getFilename()).doesNotContain(TOPIC, STEP_ID);
			}
		}
	}
}
