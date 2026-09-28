package se.sundsvall.alkt.integration.templating;

import generated.se.sundsvall.templating.RenderRequest;
import generated.se.sundsvall.templating.RenderResponse;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class TemplatingIntegration {

	private final TemplatingClient templatingClient;

	TemplatingIntegration(final TemplatingClient templatingClient) {
		this.templatingClient = templatingClient;
	}

	public String renderText(final String municipalityId, final String templateId) {
		return Optional.ofNullable(templatingClient.render(municipalityId, new RenderRequest().identifier(templateId)).getBody())
			.map(RenderResponse::getOutput)
			.map(output -> new String(Base64.getDecoder().decode(output), UTF_8))
			.orElseThrow(() -> Problem.valueOf(BAD_GATEWAY, "Template '%s' came back without content".formatted(templateId)));
	}
}
