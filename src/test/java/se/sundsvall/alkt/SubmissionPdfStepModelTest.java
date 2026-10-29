package se.sundsvall.alkt;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/** The submission PDF is the first step of the registration of every errand that came in through the form. */
class SubmissionPdfStepModelTest {

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String CAMUNDA_NAMESPACE = "http://camunda.org/schema/1.0/bpmn";
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
			Arguments.of("processmodels/notification/catering-occasion.bpmn", "end_registration_phase", "await_registration_completed"),
			Arguments.of("processmodels/notification/e-cigarette-sales.bpmn", "end_registration_phase", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-sales.bpmn", "end_registration_phase", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-serving.bpmn", "end_registration_phase", "await_registration_completed"),
			Arguments.of("processmodels/notification/low-alcohol-beer-sales-and-serving.bpmn", "end_registration_phase", "await_registration_completed"),
			Arguments.of("processmodels/notification/tobacco-free-nicotine-product-sales.bpmn", "end_registration_phase", "await_registration_completed"));
	}

	@Test
	void everyApplicationAndNotificationModelIsCovered() throws Exception {
		final var resolver = new PathMatchingResourcePatternResolver();
		final var files = new ArrayList<String>();
		for (final var folder : List.of("application", "notification")) {
			for (final Resource resource : resolver.getResources("classpath*:processmodels/" + folder + "/*.bpmn")) {
				files.add("processmodels/" + folder + "/" + resource.getFilename());
			}
		}

		assertThat(files).containsExactlyInAnyOrderElementsOf(models().map(arguments -> (String) arguments.get()[0]).toList());
	}

	@ParameterizedTest
	@MethodSource("models")
	void theStepIsTheFirstAfterTheStartOfTheRegistration(final String model, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var registration = elementById(parse(model), "registration_phase");
		final var start = children(registration, "startEvent").getFirst();

		final var step = elementById(registration.getOwnerDocument(), STEP_ID);
		assertThat(targetsFrom(registration, start.getAttribute("id"))).containsExactly(STEP_ID);
		assertThat(step.getLocalName()).isEqualTo("serviceTask");
		assertThat(step.getParentNode()).isSameAs(registration);
		assertThat(step.getAttributeNS(CAMUNDA_NAMESPACE, "type")).isEqualTo("external");
		assertThat(step.getAttributeNS(CAMUNDA_NAMESPACE, "topic")).isEqualTo(TOPIC);
	}

	@ParameterizedTest
	@MethodSource("models")
	void aDoneAndASkippedStepGoOnToTheirNextElement(final String model, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var document = parse(model);
		final var registration = elementById(document, "registration_phase");
		final var boundary = children(registration, "boundaryEvent").stream()
			.filter(event -> STEP_ID.equals(event.getAttribute("attachedToRef")))
			.findFirst()
			.orElseThrow();
		final var errorRef = ((Element) boundary.getElementsByTagNameNS(BPMN_NAMESPACE, "errorEventDefinition").item(0)).getAttribute("errorRef");

		assertThat(targetsFrom(registration, STEP_ID)).containsExactly(nextWhenDone);
		assertThat(targetsFrom(registration, boundary.getAttribute("id"))).containsExactly(nextWhenSkipped);
		assertThat(elementById(document, errorRef).getAttribute("errorCode")).isEqualTo("step_skipped");
	}

	@ParameterizedTest
	@MethodSource("models")
	void nothingIsAsynchronousBetweenTheStepAndTheNextWaitState(final String model, final String nextWhenDone, final String nextWhenSkipped) throws Exception {
		final var registration = elementById(parse(model), "registration_phase");

		for (final var id : List.of(STEP_ID, nextWhenDone, nextWhenSkipped)) {
			final var element = elementById(registration.getOwnerDocument(), id);
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

	private static List<String> targetsFrom(final Element scope, final String sourceId) {
		return children(scope, "sequenceFlow").stream()
			.filter(flow -> sourceId.equals(flow.getAttribute("sourceRef")))
			.map(flow -> flow.getAttribute("targetRef"))
			.toList();
	}

	private static List<Element> children(final Element parent, final String localName) {
		final var nodes = parent.getChildNodes();
		return IntStream.range(0, nodes.getLength())
			.mapToObj(nodes::item)
			.filter(Element.class::isInstance)
			.map(Element.class::cast)
			.filter(element -> BPMN_NAMESPACE.equals(element.getNamespaceURI()) && localName.equals(element.getLocalName()))
			.toList();
	}

	private static Element elementById(final Document document, final String id) {
		final var all = document.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
		return Optional.ofNullable(IntStream.range(0, all.getLength())
			.mapToObj(all::item)
			.map(Element.class::cast)
			.filter(element -> id.equals(element.getAttribute("id")))
			.findFirst()
			.orElse(null))
			.orElseThrow(() -> new AssertionError("No element " + id));
	}

	private static Document parse(final String model) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		try (InputStream in = SubmissionPdfStepModelTest.class.getClassLoader().getResourceAsStream(model)) {
			return factory.newDocumentBuilder().parse(in);
		}
	}
}
