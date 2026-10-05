package se.sundsvall.alkt.integration.templating;

import generated.se.sundsvall.templating.RenderRequest;
import generated.se.sundsvall.templating.RenderResponse;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class TemplatingIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String TEMPLATE_ID = "serving-permit-certificate";
	private static final Map<String, Object> PARAMETERS = Map.of("caseNumber", "IAN-2026-00209");

	@Mock
	private TemplatingClient templatingClientMock;

	@InjectMocks
	private TemplatingIntegration templatingIntegration;

	@Test
	void renderPdfAnswersWithTheDecodedDocument() {
		final var pdf = "%PDF-1.7".getBytes();
		when(templatingClientMock.renderPdf(MUNICIPALITY_ID, renderRequest())).thenReturn(ResponseEntity.ok(new RenderResponse().output(Base64.getEncoder().encodeToString(pdf))));

		assertThat(templatingIntegration.renderPdf(MUNICIPALITY_ID, TEMPLATE_ID, PARAMETERS)).isEqualTo(pdf);
	}

	@Test
	void renderPdfFailsWithoutABody() {
		when(templatingClientMock.renderPdf(MUNICIPALITY_ID, renderRequest())).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> templatingIntegration.renderPdf(MUNICIPALITY_ID, TEMPLATE_ID, PARAMETERS))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void renderPdfFailsWithoutOutput() {
		when(templatingClientMock.renderPdf(MUNICIPALITY_ID, renderRequest())).thenReturn(ResponseEntity.ok(new RenderResponse()));

		assertThatThrownBy(() -> templatingIntegration.renderPdf(MUNICIPALITY_ID, TEMPLATE_ID, PARAMETERS))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("came back without content");
	}

	@Test
	void renderPdfFailsWithoutRetryWhenTheParametersDoNotFillTheTemplate() {
		when(templatingClientMock.renderPdf(MUNICIPALITY_ID, renderRequest())).thenThrow(new ClientProblem(BAD_REQUEST, "Missing template parameter 'premisesName'"));

		assertThatThrownBy(() -> templatingIntegration.renderPdf(MUNICIPALITY_ID, TEMPLATE_ID, PARAMETERS))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Template 'serving-permit-certificate' cannot be rendered from the given parameters: ClientProblem 400")
			.hasCauseInstanceOf(ClientProblem.class);
	}

	@Test
	void renderPdfLetsAnyOtherClientProblemThrough() {
		final var notFound = new ClientProblem(NOT_FOUND, "No template");
		when(templatingClientMock.renderPdf(MUNICIPALITY_ID, renderRequest())).thenThrow(notFound);

		assertThatThrownBy(() -> templatingIntegration.renderPdf(MUNICIPALITY_ID, TEMPLATE_ID, PARAMETERS)).isSameAs(notFound);
	}

	private static RenderRequest renderRequest() {
		return new RenderRequest().identifier(TEMPLATE_ID).parameters(PARAMETERS);
	}
}
