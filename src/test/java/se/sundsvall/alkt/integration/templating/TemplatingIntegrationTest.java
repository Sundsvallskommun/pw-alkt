package se.sundsvall.alkt.integration.templating;

import generated.se.sundsvall.templating.RenderRequest;
import generated.se.sundsvall.templating.RenderResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplatingIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String TEMPLATE_ID = "alkt.processing-started";

	@Mock
	private TemplatingClient templatingClientMock;

	@InjectMocks
	private TemplatingIntegration templatingIntegration;

	@Test
	void renderTextAnswersWithTheDecodedOutput() {
		final var output = Base64.getEncoder().encodeToString("Handläggningen har påbörjats".getBytes(UTF_8));
		when(templatingClientMock.render(MUNICIPALITY_ID, new RenderRequest().identifier(TEMPLATE_ID))).thenReturn(ResponseEntity.ok(new RenderResponse().output(output)));

		assertThat(templatingIntegration.renderText(MUNICIPALITY_ID, TEMPLATE_ID)).isEqualTo("Handläggningen har påbörjats");
	}

	@Test
	void renderTextFailsWithoutABody() {
		when(templatingClientMock.render(MUNICIPALITY_ID, new RenderRequest().identifier(TEMPLATE_ID))).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> templatingIntegration.renderText(MUNICIPALITY_ID, TEMPLATE_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void renderTextFailsWithoutOutput() {
		when(templatingClientMock.render(MUNICIPALITY_ID, new RenderRequest().identifier(TEMPLATE_ID))).thenReturn(ResponseEntity.ok(new RenderResponse()));

		assertThatThrownBy(() -> templatingIntegration.renderText(MUNICIPALITY_ID, TEMPLATE_ID))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}
}
