package se.sundsvall.alkt.configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Refuses to start when a process model has the step that sends the permit certificate but no letter is configured for
 * it.
 */
@Component
class PermitCertificateLetterCheck {

	private static final String MODELS = "classpath*:processmodels/*/*.bpmn";
	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String STEP = "external_task_send_permit_certificate";
	private static final String ID_ATTRIBUTE = "id";

	PermitCertificateLetterCheck(final PermitCertificateProperties properties, final ResourcePatternResolver patternResolver)
		throws IOException, ParserConfigurationException, SAXException {
		final var missing = new ArrayList<String>();
		for (final var model : patternResolver.getResources(MODELS)) {
			processesWithStep(model).stream()
				.filter(processKey -> !properties.letters().containsKey(processKey))
				.forEach(missing::add);
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("No letter under permit-certificate.letters for the processes %s, which send the permit certificate".formatted(missing));
		}
	}

	private static List<String> processesWithStep(final Resource model) throws IOException, ParserConfigurationException, SAXException {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setNamespaceAware(true);

		try (var inputStream = model.getInputStream()) {
			final var processes = factory.newDocumentBuilder().parse(inputStream).getElementsByTagNameNS(BPMN_NAMESPACE, "process");
			return IntStream.range(0, processes.getLength())
				.mapToObj(i -> (Element) processes.item(i))
				.filter(PermitCertificateLetterCheck::hasStep)
				.map(process -> process.getAttribute(ID_ATTRIBUTE))
				.toList();
		}
	}

	private static boolean hasStep(final Element process) {
		final var elements = process.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
		return IntStream.range(0, elements.getLength())
			.mapToObj(i -> (Element) elements.item(i))
			.anyMatch(element -> STEP.equals(element.getAttribute(ID_ATTRIBUTE)));
	}
}
