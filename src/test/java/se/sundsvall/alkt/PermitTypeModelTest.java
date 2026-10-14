package se.sundsvall.alkt;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assumptions.assumeThat;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toPermitType;

/**
 * A step that creates or changes a permit takes its type from the process key, and a step that writes a decision its
 * title, so a model with such a step and no type or title would only fail once an errand reaches the step.
 */
class PermitTypeModelTest {

	private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
	private static final String CAMUNDA_NAMESPACE = "http://camunda.org/schema/1.0/bpmn";
	private static final Set<String> PERMIT_TOPICS = Set.of("CreateAssetTask", "UpdateAssetTask", "CreateChangeDraftTask", "CheckPermitTask", "CloseAssetTask");
	private static final Map<String, Consumer<String>> TITLE_OF_TOPIC = Map.of(
		"CreateDecisionTask", SupportManagementMapper::toDecisionTitle,
		"CreateChangeDraftTask", SupportManagementMapper::toChangeDraftTitle);

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
		assumeThat(topicsOf(document).stream().anyMatch(PERMIT_TOPICS::contains)).isTrue();

		assertThatNoException().isThrownBy(() -> toPermitType(processKeyOf(document)));
	}

	@ParameterizedTest
	@MethodSource("models")
	void aModelWithADecisionStepHasATitle(final Path model) throws Exception {
		final var document = read(model);
		final var processKey = processKeyOf(document);

		topicsOf(document).stream()
			.filter(TITLE_OF_TOPIC::containsKey)
			.forEach(topic -> assertThatNoException().as("%s in %s", topic, processKey).isThrownBy(() -> TITLE_OF_TOPIC.get(topic).accept(processKey)));
	}

	private static List<String> topicsOf(final Document document) {
		final var serviceTasks = document.getElementsByTagNameNS(BPMN_NAMESPACE, "serviceTask");
		return IntStream.range(0, serviceTasks.getLength())
			.mapToObj(i -> ((Element) serviceTasks.item(i)).getAttributeNS(CAMUNDA_NAMESPACE, "topic"))
			.toList();
	}

	private static String processKeyOf(final Document document) {
		return ((Element) document.getElementsByTagNameNS(BPMN_NAMESPACE, "process").item(0)).getAttribute("id");
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
