package se.sundsvall.alkt.util;

import feign.Request;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

class FailureDescriptionTest {

	@Test
	void describesAnAnswerFromAnotherServiceWithoutItsText() {
		final var problem = new ServerProblem(BAD_GATEWAY,
			"party-assets error: {detail=Party 199001012385 has no status=500 asset, status=503 Service Unavailable, title=Service Unavailable}");

		assertThat(FailureDescription.describe(problem))
			.isEqualTo("ServerProblem 502 from party-assets (remote 503 Service Unavailable)")
			.doesNotContain("199001012385");
	}

	@Test
	void describesARefusalThatKeptItsStatus() {
		final var problem = new ClientProblem(PRECONDITION_FAILED, "support-management error: {status=412 Precondition Failed, title=Precondition Failed}");

		assertThat(FailureDescription.describe(problem)).isEqualTo("ClientProblem 412 from support-management (remote 412 Precondition Failed)");
	}

	@Test
	void describesAnAnswerThatDoesNotReadAsTheDecodersByItsStatusAlone() {
		assertThat(FailureDescription.describe(new ClientProblem(BAD_GATEWAY, "Something else, status=999"))).isEqualTo("ClientProblem 502");
		assertThat(FailureDescription.describe(new ServerProblem(BAD_GATEWAY, null))).isEqualTo("ServerProblem 502");
	}

	@Test
	void keepsTheTextOfOurOwnProblems() {
		assertThat(FailureDescription.describe(Problem.valueOf(NOT_FOUND, "Errand 'errand-id' has no stakeholder with role 'PRIMARY'")))
			.isEqualTo("ThrowableProblem 404: Errand 'errand-id' has no stakeholder with role 'PRIMARY'");
		assertThat(FailureDescription.describe(Problem.valueOf(NOT_FOUND))).isEqualTo("ThrowableProblem 404");
	}

	@Test
	void namesTheTemplateParameterADecisionLeftEmpty() {
		final var problem = new ClientProblem(BAD_REQUEST,
			"templating error: {detail=Missing template parameter 'servingHours' (line 174), status=400 Bad Request, title=Bad Request}");

		assertThat(FailureDescription.describe(problem))
			.isEqualTo("ClientProblem 400 from templating (remote 400 Bad Request), missing template parameter 'servingHours'");
	}

	/** Only an identifier is kept, and only from templating, so no other text of the answer comes along. */
	@Test
	void keepsNoOtherTextOfAMissingTemplateParameter() {
		final var notAnIdentifier = new ClientProblem(BAD_REQUEST,
			"templating error: {detail=Missing template parameter 'Party 199001012385' (line 1), status=400 Bad Request, title=Bad Request}");
		final var fromAnotherService = new ClientProblem(BAD_REQUEST,
			"party-assets error: {detail=Missing template parameter 'servingHours', status=400 Bad Request, title=Bad Request}");

		assertThat(FailureDescription.describe(notAnIdentifier)).isEqualTo("ClientProblem 400 from templating (remote 400 Bad Request)");
		assertThat(FailureDescription.describe(fromAnotherService)).isEqualTo("ClientProblem 400 from party-assets (remote 400 Bad Request)");
	}

	@Test
	void namesTheServiceATokenCouldNotBeObtainedFor() {
		final var error = new OAuth2Error("invalid_client", "Client 199001012385 is unknown", null);
		final var exception = new ClientAuthorizationException(error, "party-assets", new OAuth2AuthorizationException(error));

		assertThat(FailureDescription.describe(exception))
			.isEqualTo("ClientAuthorizationException caused by OAuth2AuthorizationException for party-assets")
			.doesNotContain("199001012385");
	}

	/** A status outside 4xx and 5xx comes back from the decoder as a plain problem, with the remote text in its detail. */
	@Test
	void describesAnAnswerFromAnotherServiceByItsDetailRatherThanItsType() {
		final var problem = Problem.valueOf(BAD_GATEWAY, "party error: {detail=Moved to 199001012385, status=302 Found, title=Found}");

		assertThat(FailureDescription.describe(problem))
			.isEqualTo("ThrowableProblem 502 from party (remote 302 Found)")
			.doesNotContain("199001012385");
	}

	@Test
	void takesTheStatusTheDecoderWroteRatherThanOneInTheRemoteTitle() {
		final var problem = new ServerProblem(BAD_GATEWAY, "party-assets error: {status=503 Service Unavailable, title=Upstream answered status=500 once}");

		assertThat(FailureDescription.describe(problem)).isEqualTo("ServerProblem 502 from party-assets (remote 503 Service Unavailable)");
	}

	/** The text is ours, and a wrapped failure would otherwise repeat the class name at every level. */
	@Test
	void keepsTheTextOfAFailureNoRetryCanFix() {
		assertThat(FailureDescription.describe(new NonRetryableException("No text is configured for message 'processing-started'")))
			.isEqualTo("No text is configured for message 'processing-started'");
		assertThat(FailureDescription.describe(new NonRetryableException(null))).isEqualTo("NonRetryableException");
	}

	/** A timeout names the url it called, and a url may carry a party id, so only the kinds of failure are kept. */
	@Test
	void describesAnyOtherFailureByItsKind() {
		final var request = Request.create(Request.HttpMethod.GET, "http://party-assets/2281/assets?partyId=secret", Map.of(), null, StandardCharsets.UTF_8, null);
		final var timeout = new RetryableException(-1, "Read timed out executing GET http://party-assets/2281/assets?partyId=secret", Request.HttpMethod.GET,
			new SocketTimeoutException("Read timed out"), (Long) null, request);

		assertThat(FailureDescription.describe(timeout)).isEqualTo("RetryableException caused by SocketTimeoutException");
		assertThat(FailureDescription.describe(new IllegalStateException("Boom"))).isEqualTo("IllegalStateException");
	}
}
