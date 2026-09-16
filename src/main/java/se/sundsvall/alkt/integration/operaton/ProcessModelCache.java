package se.sundsvall.alkt.integration.operaton;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import static se.sundsvall.dept44.util.LogUtils.sanitizeForLogging;

/**
 * What a process model says about itself: the display name of an element, and which phase an element sits in. Read from
 * the deployed XML, since the runtime API answers with ids alone. See the README section on manual gates.
 */
@Component
public class ProcessModelCache {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessModelCache.class);

	private static final ErrorHandler RETHROWING = new ErrorHandler() {

		@Override
		public void warning(final SAXParseException e) {
			// A warning leaves a usable document, so it is not worth a log line of its own.
		}

		@Override
		public void error(final SAXParseException e) throws SAXParseException {
			throw e;
		}

		@Override
		public void fatalError(final SAXParseException e) throws SAXParseException {
			throw e;
		}
	};

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String SUB_PROCESS = "subProcess";
	private static final String ID_ATTRIBUTE = "id";
	private static final String NAME_ATTRIBUTE = "name";

	// The cap keeps a long-lived pod from holding every version it ever saw.
	private static final int MAX_MODELS = 100;

	// A deployed definition never changes, so an entry never goes stale. One entry per deployed version, and the map is
	// gone at restart.
	private final Map<String, ProcessModel> models = new ConcurrentHashMap<>();

	private final OperatonClient operatonClient;

	ProcessModelCache(final OperatonClient operatonClient) {
		this.operatonClient = operatonClient;
	}

	public ProcessModel modelOf(final String processDefinitionId) {
		return Optional.ofNullable(models.get(processDefinitionId))
			.orElseGet(() -> load(processDefinitionId));
	}

	// A model that could not be read is not cached: the next report tries again rather than living with empty labels.
	private ProcessModel load(final String processDefinitionId) {
		try {
			final var model = parse(operatonClient.getProcessDefinitionXml(processDefinitionId).getBpmn20Xml());
			// Cleared rather than evicted one by one: rereading the handful of definitions in use costs one call each.
			if (models.size() >= MAX_MODELS) {
				models.clear();
			}
			models.put(processDefinitionId, model);
			return model;
		} catch (final Exception e) {
			LOG.warn("Could not read the model of process definition {}, falling back to message names", sanitizeForLogging(processDefinitionId), e);
			return ProcessModel.EMPTY;
		}
	}

	private static ProcessModel parse(final String xml) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setNamespaceAware(true);

		final var builder = factory.newDocumentBuilder();
		// Without a handler of its own the parser prints every fatal error to stderr before throwing, and the throw is
		// what this class acts on.
		builder.setErrorHandler(RETHROWING);

		final var document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		final var names = new HashMap<String, String>();
		final var phases = new HashMap<String, String>();

		final var elements = document.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
		for (var i = 0; i < elements.getLength(); i++) {
			final var element = (Element) elements.item(i);
			final var id = element.getAttribute(ID_ATTRIBUTE);
			if (id.isBlank()) {
				continue;
			}
			Optional.of(element.getAttribute(NAME_ATTRIBUTE))
				.filter(name -> !name.isBlank())
				.ifPresent(name -> names.put(id, name));
			enclosingPhaseOf(element).ifPresent(phase -> phases.put(id, phase));
		}

		return new ProcessModel(Map.copyOf(names), Map.copyOf(phases));
	}

	/**
	 * The outermost subprocess rather than the nearest one. A phase may hold a subprocess of its own, and it is the phase
	 * Support Management shows, not whatever the modeller nested inside it.
	 */
	private static Optional<String> enclosingPhaseOf(final Element element) {
		var phase = Optional.<String>empty();
		for (var parent = element.getParentNode(); parent instanceof final Element candidate; parent = parent.getParentNode()) {
			if (isSubProcess(candidate)) {
				phase = Optional.of(candidate.getAttribute(ID_ATTRIBUTE));
			}
		}
		return phase;
	}

	private static boolean isSubProcess(final Node node) {
		return BPMN_NAMESPACE.equals(node.getNamespaceURI()) && SUB_PROCESS.equals(node.getLocalName());
	}

	/** The names and the phase of every element of one process definition. */
	public record ProcessModel(Map<String, String> names, Map<String, String> phases) {

		public static final ProcessModel EMPTY = new ProcessModel(Map.of(), Map.of());

		/** The fallback is the name of the message, which is what the story asks for when the model says nothing. */
		public String labelOf(final String activityId, final String fallback) {
			return Optional.ofNullable(names.get(activityId)).orElse(fallback);
		}

		/** Empty for an element that sits directly in the process rather than in a phase. */
		public Optional<ModelElement> phaseOf(final String activityId) {
			return Optional.ofNullable(phases.get(activityId))
				.map(phaseId -> new ModelElement(phaseId, names.get(phaseId)));
		}
	}

	public record ModelElement(String id, String name) {}
}
