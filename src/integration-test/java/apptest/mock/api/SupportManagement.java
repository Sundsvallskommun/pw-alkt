package apptest.mock.api;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class SupportManagement {

	public static void mockPatchErrand(final String municipalityId, final String namespace, final String errandId) {
		final var errandPath = "/api-support-management/%s/%s/errands/%s".formatted(municipalityId, namespace, errandId);
		stubFor(patch(urlEqualTo(errandPath)).willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));
	}
}
