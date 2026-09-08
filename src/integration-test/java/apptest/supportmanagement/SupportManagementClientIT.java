package apptest.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

	@BeforeEach
	void setIdentity() {
		RequestId.init("test-request-id");
		Identifier.set(Identifier.parse("pw-alkt; type=processEngine"));
	}

	@AfterEach
	void clearIdentity() {
		Identifier.remove();
		RequestId.reset();
	}

	@Test
	void getErrandReturnsTheETagFromTheResponseHeader() {
		mockApiGatewayToken();
		stubFor(get(urlEqualTo("/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)))
			.willReturn(okJson("{}").withHeader("ETag", "\"7\"").withHeader("Content-Encoding", "identity")));

		final var response = supportManagementClient.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);

		assertThat(response.getStatusCode()).isEqualTo(OK);
		assertThat(response.getHeaders().getETag()).isEqualTo("\"7\"");
	}

	@Test
	void patchErrandSendsTheEtagAsIfMatch() {
		mockApiGatewayToken();
		stubFor(patch(urlEqualTo("/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)))
			.withHeader("If-Match", equalTo("\"7\""))
			.willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));

		final var response = supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "\"7\"", new Errand());

		assertThat(response.getStatusCode()).isEqualTo(OK);
	}

	@Test
	void patchErrandWithAStaleIfMatchThrows() {
		mockApiGatewayToken();
		stubFor(patch(urlEqualTo("/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)))
			.willReturn(aResponse()
				.withStatus(412)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Precondition Failed\",\"status\":412}")));

		assertThatThrownBy(() -> supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "\"6\"", new Errand()))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void sendsIdentityHeadersOnBothTheReadAndTheWrite() {
		mockApiGatewayToken();
		final var errandPath = "/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		stubFor(get(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("ETag", "\"7\"").withHeader("Content-Encoding", "identity")));
		stubFor(patch(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));

		supportManagementClient.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "\"7\"", new Errand());

		verify(getRequestedFor(urlEqualTo(errandPath)).withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Request-Group-Id", equalTo("test-request-id")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Trigger-Process", equalTo("false")));
	}
}
