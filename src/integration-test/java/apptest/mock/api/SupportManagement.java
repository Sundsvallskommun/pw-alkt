package apptest.mock.api;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/** Stubs for the Support Management process endpoints. Paths are absolute under the api-gateway prefix. */
public class SupportManagement {

	private static final String BASE = "/api-support-management";

	public static String reportPath(final String municipalityId, final String namespace, final String errandId, final String processInstanceId) {
		return "%s/%s/%s/errands/%s/processes/%s".formatted(BASE, municipalityId, namespace, errandId, processInstanceId);
	}

	/** Accepts every report on the errand. */
	public static void mockReportProcess(final String municipalityId, final String namespace, final String errandId) {
		stubFor(put(urlPathMatching(reportPath(municipalityId, namespace, errandId, "[^/]+")))
			.willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));
	}
}
