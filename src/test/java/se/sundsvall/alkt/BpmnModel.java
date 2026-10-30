package se.sundsvall.alkt;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Reads the process models as XML for tests that check their structure. */
final class BpmnModel {

	static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	static final String CAMUNDA_NAMESPACE = "http://camunda.org/schema/1.0/bpmn";

	private final Document document;

	private BpmnModel(final Document document) {
		this.document = document;
	}

	static BpmnModel read(final String model) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		try (InputStream in = BpmnModel.class.getClassLoader().getResourceAsStream(model)) {
			return new BpmnModel(factory.newDocumentBuilder().parse(in));
		}
	}

	static List<String> modelsIn(final String folder) throws Exception {
		final var files = new ArrayList<String>();
		for (final Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:processmodels/" + folder + "/*.bpmn")) {
			files.add("processmodels/" + folder + "/" + resource.getFilename());
		}
		return files;
	}

	Element element(final String id) {
		final var all = document.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
		return IntStream.range(0, all.getLength())
			.mapToObj(all::item)
			.map(Element.class::cast)
			.filter(element -> id.equals(element.getAttribute("id")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No element " + id));
	}

	/** The targets of the flows leaving the element, in the subprocess or process that holds it. */
	List<String> targetsFrom(final String sourceId) {
		final var scope = (Element) element(sourceId).getParentNode();
		return children(scope, "sequenceFlow").stream()
			.filter(flow -> sourceId.equals(flow.getAttribute("sourceRef")))
			.map(flow -> flow.getAttribute("targetRef"))
			.toList();
	}

	/** The flow from source to target. */
	Element flow(final String sourceId, final String targetId) {
		final var scope = (Element) element(sourceId).getParentNode();
		return children(scope, "sequenceFlow").stream()
			.filter(flow -> sourceId.equals(flow.getAttribute("sourceRef")) && targetId.equals(flow.getAttribute("targetRef")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No flow from %s to %s".formatted(sourceId, targetId)));
	}

	String conditionOf(final String sourceId, final String targetId) {
		return flow(sourceId, targetId).getElementsByTagNameNS(BPMN_NAMESPACE, "conditionExpression").item(0).getTextContent().trim();
	}

	/** The boundary event attached to the element. */
	Element boundaryOf(final String attachedToId) {
		final var scope = (Element) element(attachedToId).getParentNode();
		return children(scope, "boundaryEvent").stream()
			.filter(event -> attachedToId.equals(event.getAttribute("attachedToRef")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No boundary event on " + attachedToId));
	}

	/** The name of the message a catch event waits for, or null when it waits for none. */
	String messageNameOf(final String catchEventId) {
		final var definitions = element(catchEventId).getElementsByTagNameNS(BPMN_NAMESPACE, "messageEventDefinition");
		if (definitions.getLength() == 0) {
			return null;
		}
		return element(((Element) definitions.item(0)).getAttribute("messageRef")).getAttribute("name");
	}

	String parentIdOf(final String id) {
		return ((Element) element(id).getParentNode()).getAttribute("id");
	}

	String typeOf(final String id) {
		return element(id).getLocalName();
	}

	String topicOf(final String id) {
		return element(id).getAttributeNS(CAMUNDA_NAMESPACE, "topic");
	}

	/** The ids of the subprocess and every element inside it that is asynchronous before or after. */
	List<String> asynchronousElementsIn(final String subProcessId) {
		final var subProcess = element(subProcessId);
		final var all = subProcess.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
		return Stream.concat(Stream.of(subProcess), IntStream.range(0, all.getLength()).mapToObj(all::item).map(Element.class::cast))
			.filter(element -> "true".equals(element.getAttributeNS(CAMUNDA_NAMESPACE, "asyncBefore")) || "true".equals(element.getAttributeNS(CAMUNDA_NAMESPACE, "asyncAfter")))
			.map(element -> element.getAttribute("id"))
			.toList();
	}

	/** The ids of every message catch event inside the subprocess. */
	List<String> messageCatchEventsIn(final String subProcessId) {
		final var all = element(subProcessId).getElementsByTagNameNS(BPMN_NAMESPACE, "intermediateCatchEvent");
		return IntStream.range(0, all.getLength())
			.mapToObj(all::item)
			.map(Element.class::cast)
			.filter(element -> element.getElementsByTagNameNS(BPMN_NAMESPACE, "messageEventDefinition").getLength() > 0)
			.map(element -> element.getAttribute("id"))
			.toList();
	}

	static List<Element> children(final Element parent, final String localName) {
		final var nodes = parent.getChildNodes();
		return IntStream.range(0, nodes.getLength())
			.mapToObj(nodes::item)
			.filter(Element.class::isInstance)
			.map(Element.class::cast)
			.filter(element -> BPMN_NAMESPACE.equals(element.getNamespaceURI()) && localName.equals(element.getLocalName()))
			.toList();
	}
}
