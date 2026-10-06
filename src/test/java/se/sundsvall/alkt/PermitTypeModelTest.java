package se.sundsvall.alkt;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assumptions.assumeThat;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitType;

/**
 * A step that creates or changes a permit takes its type from the process key, so a model with such a step and no
 * permit type would only fail once an errand reaches the step.
 */
class PermitTypeModelTest {

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String CAMUNDA_NAMESPACE = "http://camunda.org/schema/1.0/bpmn";
	private static final Set<String> PERMIT_TOPICS = Set.of("CreateAssetTask", "UpdateAssetTask", "CreateChangeDraftTask", "CheckPermitTask", "CloseAssetTask");

	private static Stream<Path> models() throws Exception {
		final var root = Path.of(PermitTypeModelTest.class.getClassLoader().getResource("processmodels").toURI());
		try (final var paths = Files.walk(root)) {
			return paths.filter(path -> path.toString().endsWith(".bpmn")).toList().stream();
		}
	}

	@ParameterizedTest
	@MethodSource("models")
	void aModelWithAPermitStepHasAPermitType(final Path model) throws Exception {
		final var document = read(model);
		final var serviceTasks = document.getElementsByTagNameNS(BPMN_NAMESPACE, "serviceTask");
		assumeThat(IntStream.range(0, serviceTasks.getLength())
			.mapToObj(i -> ((Element) serviceTasks.item(i)).getAttributeNS(CAMUNDA_NAMESPACE, "topic"))
			.anyMatch(PERMIT_TOPICS::contains)).isTrue();

		final var processKey = ((Element) document.getElementsByTagNameNS(BPMN_NAMESPACE, "process").item(0)).getAttribute("id");

		assertThatNoException().isThrownBy(() -> toPermitType(processKey));
	}

	private static Document read(final Path model) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setNamespaceAware(true);
		try (final InputStream xml = Files.newInputStream(model)) {
			return factory.newDocumentBuilder().parse(xml);
		}
	}
}
