package apptest.openapi;

import static java.nio.file.Files.writeString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import se.sundsvall.alkt.Application;
import se.sundsvall.dept44.util.ResourceUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Guards the checked in OpenAPI specification against drift. The specification that the running application exposes on
 * {@code /api-docs.yaml} is compared to {@code src/test/resources/api/openapi.yaml}, so an API change that is not
 * reflected in the committed specification breaks the build.
 * <p>
 * The context is started without an engine: auto deployment is turned off and the external task client is kept from
 * fetching, since neither an Operaton engine nor the WireMock server is running for this test - only the REST layer is
 * needed to render the specification.
 * <p>
 * When the specification legitimately changes, copy {@code target/generated-api.yaml} (written by this test) over
 * {@code src/test/resources/api/openapi.yaml}.
 */
@ActiveProfiles("it")
@AutoConfigureTestRestTemplate
@SpringBootTest(
	webEnvironment = RANDOM_PORT,
	classes = Application.class,
	properties = {
		"spring.main.banner-mode=off",
		"logging.level.se.sundsvall.dept44.payload=OFF",
		// The it profile renames the application to pw-alkt-it, which would leak into the specification title. The
		// committed specification should describe the deployed service, so the production name is restored here.
		"spring.application.name=pw-alkt",
		"wiremock.server.port=10101",
		"process-engine.deployment.autoDeployEnabled=false",
		"camunda.bpm.client.disable-auto-fetching=true"
	})
class OpenApiSpecificationIT {

	private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

	@Value("${openapi.name}")
	private String openApiName;

	@Value("${openapi.version}")
	private String openApiVersion;

	@Value("classpath:/api/openapi.yaml")
	private Resource openApiResource;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void compareOpenApiSpecifications() throws IOException {
		final var existingOpenApiSpecification = ResourceUtils.asString(openApiResource);
		final var currentOpenApiSpecification = getCurrentOpenApiSpecification();

		writeString(Path.of("target/generated-api.yaml"), currentOpenApiSpecification);

		assertThatJson(toJson(currentOpenApiSpecification))
			.withOptions(List.of(IGNORING_ARRAY_ORDER))
			.whenIgnoringPaths("servers")
			.isEqualTo(toJson(existingOpenApiSpecification));
	}

	/**
	 * Fetches and returns the current OpenAPI specification in YAML format.
	 *
	 * @return the current OpenAPI specification
	 */
	private String getCurrentOpenApiSpecification() {
		final var uri = UriComponentsBuilder.fromPath("/api-docs.yaml")
			.buildAndExpand(openApiName, openApiVersion)
			.toUri();

		return restTemplate.getForObject(uri, String.class);
	}

	/**
	 * Attempts to convert the given YAML (no YAML-check...) to JSON.
	 *
	 * @param  yaml the YAML to convert
	 * @return      a JSON string
	 */
	private String toJson(final String yaml) {
		try {
			return YAML_MAPPER.readTree(yaml).toString();
		} catch (final JacksonException e) {
			throw new IllegalStateException("Unable to convert YAML to JSON", e);
		}
	}
}
