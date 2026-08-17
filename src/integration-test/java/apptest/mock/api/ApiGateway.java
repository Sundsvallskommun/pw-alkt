package apptest.mock.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static wiremock.org.eclipse.jetty.http.HttpStatus.OK_200;

public class ApiGateway {

    public static void mockApiGatewayToken() {
        stubFor(post(urlEqualTo("/api-gateway/token"))
                .withBasicAuth("the-client-id", "the-client-secret")
                .willReturn(aResponse()
                        .withStatus(OK_200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("common/responses/api-gateway-retrieve-token.json")));
    }
}
