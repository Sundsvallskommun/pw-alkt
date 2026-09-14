package apptest.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandProcess;
import generated.se.sundsvall.supportmanagement.ProcessError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static apptest.mock.api.ApiGateway.mockApiGatewayToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CREATED;
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
	private static final String PROCESS_INSTANCE_ID = "8f1c2b6e-1f4a-4d61-9a0e-2b7c1f0a5e33";

	@Autowired
	private SupportManagementClient supportManagementClient;

	@BeforeEach
	void setIdentity() {
		RequestId.init("test-request-id");
	}

	@AfterEach
	void clearIdentity() {
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
		final var errandPath = "/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		stubFor(get(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("ETag", "\"7\"").withHeader("Content-Encoding", "identity")));
		stubFor(patch(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));

		final var etag = supportManagementClient.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID).getHeaders().getETag();
		final var response = supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, etag, null, new Errand());

		assertThat(response.getStatusCode()).isEqualTo(OK);
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("If-Match", equalTo("\"7\"")));
	}

	@Test
	void patchErrandWithAStaleIfMatchThrows() {
		mockApiGatewayToken();
		stubFor(patch(urlEqualTo("/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID)))
			.willReturn(aResponse()
				.withStatus(412)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Precondition Failed\",\"status\":412}")));

		assertThatThrownBy(() -> supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "\"6\"", null, new Errand()))
			.isInstanceOf(ClientProblem.class);
	}

	@Test
	void sendsIdentityHeadersOnBothTheReadAndTheWrite() {
		mockApiGatewayToken();
		final var errandPath = "/api-support-management/%s/%s/errands/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		stubFor(get(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("ETag", "\"7\"").withHeader("Content-Encoding", "identity")));
		stubFor(patch(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));

		supportManagementClient.getErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		supportManagementClient.patchErrand(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, "\"7\"", false, new Errand());

		verify(getRequestedFor(urlEqualTo(errandPath)).withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Request-Group-Id", equalTo("test-request-id")));
		verify(patchRequestedFor(urlEqualTo(errandPath)).withHeader("X-Trigger-Process", equalTo("false")));
	}

	@Test
	void reportProcessSendsTheReportWithTheIdentityHeadersAndNoTrigger() {
		mockApiGatewayToken();
		final var processPath = "/api-support-management/%s/%s/errands/%s/processes/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID);
		stubFor(put(urlEqualTo(processPath)).willReturn(aResponse()
			.withStatus(201)
			.withHeader("Content-Type", "application/json")
			.withHeader("Content-Encoding", "identity")
			.withBody("{\"processInstanceId\":\"%s\",\"processStatus\":\"FAILED\"}".formatted(PROCESS_INSTANCE_ID))));

		final var report = new ErrandProcess()
			.processService("pw-alkt")
			.processKey("alcohol-serving")
			.processStatus("FAILED")
			.error(new ProcessError().code("INCIDENT").message("Timeout against Employee after 30 s"));

		final var response = supportManagementClient.reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report);

		assertThat(response.getStatusCode()).isEqualTo(CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getProcessStatus()).isEqualTo("FAILED");
		verify(putRequestedFor(urlEqualTo(processPath))
			.withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine"))
			.withHeader("X-Request-Group-Id", equalTo("test-request-id"))
			.withoutHeader("X-Trigger-Process")
			.withRequestBody(equalToJson("""
				{"processService":"pw-alkt","processKey":"alcohol-serving","processStatus":"FAILED",
				 "error":{"code":"INCIDENT","message":"Timeout against Employee after 30 s"}}""", true, true)));
	}

	@Test
	void reportProcessAtAStaleErrandVersionThrows() {
		mockApiGatewayToken();
		stubFor(put(urlEqualTo("/api-support-management/%s/%s/errands/%s/processes/%s".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID)))
			.withRequestBody(matchingJsonPath("$.errandVersion", equalTo("99")))
			.willReturn(aResponse()
				.withStatus(412)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Precondition Failed\",\"status\":412}")));

		final var report = new ErrandProcess().processService("pw-alkt").processKey("alcohol-serving").processStatus("COMPLETED").errandVersion(99L);

		assertThatThrownBy(() -> supportManagementClient.reportProcess(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID, PROCESS_INSTANCE_ID, report))
			.isInstanceOf(ClientProblem.class)
			.hasMessageContaining("Precondition Failed");
	}

	@Test
	void getErrandProcessesReadsTheRowsOfTheErrand() {
		mockApiGatewayToken();
		final var processesPath = "/api-support-management/%s/%s/errands/%s/processes".formatted(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);
		stubFor(get(urlEqualTo(processesPath)).willReturn(okJson("""
			{"processes":[{"processInstanceId":"%s","processKey":"alcohol-serving","processStatus":"RUNNING"}]}""".formatted(PROCESS_INSTANCE_ID))
			.withHeader("Content-Encoding", "identity")));

		final var response = supportManagementClient.getErrandProcesses(MUNICIPALITY_ID, NAMESPACE, ERRAND_ID);

		assertThat(response.getStatusCode()).isEqualTo(OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getProcesses()).singleElement().satisfies(process -> {
			assertThat(process.getProcessInstanceId()).isEqualTo(PROCESS_INSTANCE_ID);
			assertThat(process.getProcessStatus()).isEqualTo("RUNNING");
		});
		verify(getRequestedFor(urlEqualTo(processesPath)).withHeader("X-Sent-By", equalTo("pw-alkt; type=processEngine")));
	}
}
