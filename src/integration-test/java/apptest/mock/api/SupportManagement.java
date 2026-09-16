package apptest.mock.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/** Stubs for the Support Management process endpoints. Paths are absolute under the api-gateway prefix. */
public class SupportManagement {

	private static final String BASE = "/api-support-management";
	private static final int SPECIFIC = 1;
	private static final int FALLBACK = 10;

	public static String reportPath(final String municipalityId, final String namespace, final String errandId, final String processInstanceId) {
		return "%s/%s/%s/errands/%s/processes/%s".formatted(BASE, municipalityId, namespace, errandId, processInstanceId);
	}

	public static String processesPath(final String municipalityId, final String namespace, final String errandId) {
		return "%s/%s/%s/errands/%s/processes".formatted(BASE, municipalityId, namespace, errandId);
	}

	/** Accepts every report on the errand. */
	public static void mockReportProcess(final String municipalityId, final String namespace, final String errandId) {
		stubFor(put(urlPathMatching(processesPath(municipalityId, namespace, errandId) + "/[^/]+")).atPriority(SPECIFIC)
			.willReturn(okJson("{}").withHeader("Content-Encoding", "identity")));
	}

	/** Answers every report on the errand with 500, the way a Support Management outage looks to a work step. */
	public static void mockReportProcessDown(final String municipalityId, final String namespace, final String errandId) {
		stubFor(put(urlPathMatching(processesPath(municipalityId, namespace, errandId) + "/[^/]+")).atPriority(SPECIFIC)
			.willReturn(aResponse()
				.withStatus(500)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Internal Server Error\",\"status\":500}")));
	}

	/**
	 * Refuses every report on the errand with 412: the errand moved under the step. The one refusal a work step does not
	 * swallow, so with no retries the engine raises an incident.
	 */
	public static void mockReportProcessRefused(final String municipalityId, final String namespace, final String errandId) {
		stubFor(put(urlPathMatching(processesPath(municipalityId, namespace, errandId) + "/[^/]+")).atPriority(SPECIFIC)
			.willReturn(aResponse()
				.withStatus(412)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Precondition Failed\",\"status\":412}")));
	}

	/** One row on the errand, in the given state, with or without an error code. */
	public static void mockGetErrandProcesses(final String municipalityId, final String namespace, final String errandId, final String processInstanceId, final String status, final String errorCode) {
		var error = "null";
		if (errorCode != null) {
			error = "{\"code\":\"%s\"}".formatted(errorCode);
		}
		stubFor(get(urlPathEqualTo(processesPath(municipalityId, namespace, errandId))).atPriority(SPECIFIC)
			.willReturn(okJson("""
				{"processes":[{"processInstanceId":"%s","processKey":"alcohol-serving","processStatus":"%s","error":%s}]}""".formatted(processInstanceId, status, error))
				.withHeader("Content-Encoding", "identity")));
	}

	/**
	 * Every other errand is gone. The engine is shared between test classes, so a reconciliation run sees instances of
	 * errands no test in this class ever stubbed. Answering 404 for them keeps the run from tripping over an unmatched
	 * request.
	 */
	public static void mockOtherErrandsGone() {
		stubFor(get(urlPathMatching(BASE + "/[^/]+/[^/]+/errands/[^/]+/processes")).atPriority(FALLBACK)
			.willReturn(aResponse()
				.withStatus(404)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"title\":\"Not Found\",\"status\":404}")));
	}
}
