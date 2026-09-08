package apptest.supportmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@WireMockAppTestSuite(files = "classpath:/Wiremock/", classes = Application.class)
@TestPropertySource(properties = {
	"process-engine.deployment.autoDeployEnabled=false",
	"camunda.bpm.client.disable-auto-fetching=true"
})
class SupportManagementClientIT extends AbstractAppTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "ALKT";
	private static final String ERRAND_ID = "f0882f1d-06bc-47fd-b017-1d8307f5ce95";

	@Autowired
	private SupportManagementClient supportManagementClient;

	@Test
	void getErrandReturnsTheETagFromTheResponseHeader() {
		mockApiGatewayToken();
		stubFor(get(urlEqualTo("/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)))
			.willReturn(okJson("{}").withHeader("ETag", "\"7\"").withHeader("Content-Encoding", "identity")));

		final var response = supportManagementClient.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);

		assertThat(response.getStatusCode()).isEqualTo(OK);
		assertThat(response.getHeaders().getETag()).isEqualTo("\"7\"");
	}
}
