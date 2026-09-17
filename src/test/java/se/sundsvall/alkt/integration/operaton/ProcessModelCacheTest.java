package se.sundsvall.alkt.integration.operaton;

import generated.se.sundsvall.operaton.ProcessDefinitionDiagramDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.exception.ClientProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class ProcessModelCacheTest {

	private static final String DEFINITION_ID = "alcohol-serving:1:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a";
	// Mirrors ProcessModelCache.MAX_MODELS, which is private
	private static final int MAX_MODELS = 100;

	@Mock
	private OperatonClient operatonClientMock;

	@InjectMocks
	private ProcessModelCache processModelCache;

	private String modelXml;

	@BeforeEach
	void setUp() throws IOException {
		try (var model = getClass().getResourceAsStream("/processmodels/alcohol-serving.bpmn")) {
			modelXml = new String(model.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void readsTheNameAndThePhaseOfACatchEvent() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		final var model = processModelCache.modelOf(DEFINITION_ID);

		assertThat(model.labelOf("await_review_completed", "review_completed")).isEqualTo("Review completed");
		assertThat(model.phaseOf("await_review_completed")).hasValueSatisfying(phase -> {
			assertThat(phase.id()).isEqualTo("review_phase");
			assertThat(phase.name()).isEqualTo("Review");
		});
	}

	@Test
	void hasNoPhaseForAnElementOutsideThePhases() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		assertThat(processModelCache.modelOf(DEFINITION_ID).phaseOf("external_task_complete_process")).isEmpty();
	}

	@Test
	void fallsBackToTheMessageNameForAnUnknownActivity() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		final var model = processModelCache.modelOf(DEFINITION_ID);

		assertThat(model.labelOf("await_nothing_completed", "nothing_completed")).isEqualTo("nothing_completed");
		assertThat(model.phaseOf("await_nothing_completed")).isEmpty();
	}

	@Test
	void readsADefinitionOnce() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		processModelCache.modelOf(DEFINITION_ID);
		final var second = processModelCache.modelOf(DEFINITION_ID);

		assertThat(second.labelOf("await_review_completed", "review_completed")).isEqualTo("Review completed");
		verify(operatonClientMock).getProcessDefinitionXml(DEFINITION_ID);
		verifyNoMoreInteractions(operatonClientMock);
	}

	@Test
	void triesAgainAfterAFailedLookup() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID))
			.thenThrow(new ClientProblem(BAD_GATEWAY, "Operaton is down"))
			.thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		assertThat(processModelCache.modelOf(DEFINITION_ID).names()).isEmpty();
		assertThat(processModelCache.modelOf(DEFINITION_ID).labelOf("await_review_completed", "review_completed")).isEqualTo("Review completed");

		verify(operatonClientMock, times(2)).getProcessDefinitionXml(DEFINITION_ID);
	}

	/** A phase may hold a subprocess of its own, and the phase is what Support Management shows. */
	@Test
	void namesTheOutermostSubProcessAsThePhase() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml("""
			<?xml version="1.0" encoding="UTF-8"?>
			<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_nested">
			  <bpmn:process id="nested" name="Nested">
			    <bpmn:subProcess id="review_phase" name="Review">
			      <bpmn:subProcess id="inner_loop" name="Inner loop">
			        <bpmn:intermediateCatchEvent id="await_review_completed" name="Review completed" />
			      </bpmn:subProcess>
			    </bpmn:subProcess>
			  </bpmn:process>
			</bpmn:definitions>"""));

		assertThat(processModelCache.modelOf(DEFINITION_ID).phaseOf("await_review_completed")).hasValueSatisfying(phase -> {
			assertThat(phase.id()).isEqualTo("review_phase");
			assertThat(phase.name()).isEqualTo("Review");
		});
	}

	/** A boundary event hangs on a phase rather than inside it, so it belongs to no phase and reports under its own id. */
	@Test
	void givesABoundaryEventOnAPhaseNoPhase() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml("""
			<?xml version="1.0" encoding="UTF-8"?>
			<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_boundary">
			  <bpmn:process id="boundary" name="Boundary">
			    <bpmn:subProcess id="review_phase" name="Review" />
			    <bpmn:boundaryEvent id="await_review_withdrawn" name="Review withdrawn" attachedToRef="review_phase" />
			  </bpmn:process>
			</bpmn:definitions>"""));

		final var model = processModelCache.modelOf(DEFINITION_ID);

		assertThat(model.phaseOf("await_review_withdrawn")).isEmpty();
		assertThat(model.labelOf("await_review_withdrawn", "review_withdrawn")).isEqualTo("Review withdrawn");
	}

	/** The map is bounded, so a pod outliving a hundred deployed versions does not hold them all. */
	@Test
	void readsADefinitionAgainOnceTheCapClearedIt() {
		when(operatonClientMock.getProcessDefinitionXml(any())).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml(modelXml));

		processModelCache.modelOf(DEFINITION_ID);
		for (var version = 2; version <= MAX_MODELS + 1; version++) {
			processModelCache.modelOf("alcohol-serving:%d:3c3755ad-b1a7-11f1-af7f-7aca4f79b75a".formatted(version));
		}

		assertThat(processModelCache.modelOf(DEFINITION_ID).labelOf("await_review_completed", "review_completed")).isEqualTo("Review completed");
		verify(operatonClientMock, times(2)).getProcessDefinitionXml(DEFINITION_ID);
	}

	@Test
	void answersEmptyForAModelItCannotParse() {
		when(operatonClientMock.getProcessDefinitionXml(DEFINITION_ID)).thenReturn(new ProcessDefinitionDiagramDto().bpmn20Xml("<bpmn:definitions"));

		final var model = processModelCache.modelOf(DEFINITION_ID);

		assertThat(model.names()).isEmpty();
		assertThat(model.phases()).isEmpty();
		assertThat(model.labelOf("await_review_completed", "review_completed")).isEqualTo("review_completed");
	}
}
