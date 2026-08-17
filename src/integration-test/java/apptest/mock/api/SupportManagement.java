package apptest.mock.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static wiremock.org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static wiremock.org.eclipse.jetty.http.HttpStatus.OK_200;

import com.github.tomakehurst.wiremock.matching.ContentPattern;

import java.util.UUID;

public class SupportManagement {

	public static String mockSupportManagementPost(final String municipalityId, final String namespace, final String scenarioName, final String requiredScenarioState, final String newScenarioState,
		final ContentPattern<?> bodyPattern) {
		final var smErrandId = UUID.randomUUID().toString();
		return stubFor(post(String.format("/api-support-management/%s/%s/errands", municipalityId, namespace))
			.inScenario(scenarioName)
			.whenScenarioStateIs(requiredScenarioState)
			.withHeader("Authorization", equalTo("Bearer MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3"))
			.withRequestBody(bodyPattern)
			.willReturn(aResponse()
				.withStatus(CREATED_201)
				.withHeader("Content-Type", "application/json")
				.withHeader("Location", String.format("/api-support-management/%s/%s/errands/%s", municipalityId, namespace, smErrandId)))
			.willSetStateTo(newScenarioState))
			.getNewScenarioState();
	}

	public static String mockSupportManagementGetLabels(final String municipalityId, final String namespace, final String scenarioName, final String requiredScenarioState, final String newScenarioState) {
		return stubFor(get(urlEqualTo(String.format("/api-support-management/%s/%s/metadata/labels", municipalityId, namespace)))
			.inScenario(scenarioName)
			.whenScenarioStateIs(requiredScenarioState)
			.withHeader("Authorization", equalTo("Bearer MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3"))
			.willReturn(aResponse()
				.withStatus(OK_200)
				.withHeader("Content-Type", "application/json")
				.withBodyFile("common/responses/supportmanagement/get-metadata-labels.json"))
			.willSetStateTo(newScenarioState)).getNewScenarioState();
	}
}
