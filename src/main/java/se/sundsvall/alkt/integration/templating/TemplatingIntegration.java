package se.sundsvall.alkt.integration.templating;

import generated.se.sundsvall.templating.RenderRequest;
import generated.se.sundsvall.templating.RenderResponse;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.alkt.util.FailureDescription.describe;

@Component
public class TemplatingIntegration {

	private final TemplatingClient templatingClient;

	TemplatingIntegration(final TemplatingClient templatingClient) {
		this.templatingClient = templatingClient;
	}

	/** A 400 means the parameters do not fill the template, which no retry fixes. */
	public byte[] renderPdf(final String municipalityId, final String templateId, final Map<String, Object> parameters) {
		final ResponseEntity<RenderResponse> response;
		try {
			response = templatingClient.renderPdf(municipalityId, new RenderRequest().identifier(templateId).parameters(parameters));
		} catch (final ClientProblem e) {
			if (BAD_REQUEST.equals(e.getStatus())) {
				throw new NonRetryableException("Template '%s' cannot be rendered from the given parameters: %s".formatted(templateId, describe(e)), e);
			}
			throw e;
		}

		return Optional.ofNullable(response.getBody())
			.map(RenderResponse::getOutput)
			.map(Base64.getDecoder()::decode)
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Template '%s' came back without content".formatted(templateId)));
	}
}
