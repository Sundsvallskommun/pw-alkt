package se.sundsvall.alkt.util;

import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.exception.NonRetryableException;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.alkt.util.RemoteAnswers.failWithoutRetryOn;

class RemoteAnswersTest {

	@Test
	void failWithoutRetryOnAnswersWithTheAnswer() {
		assertThat(failWithoutRetryOn(NOT_FOUND, "Asset 'id' cannot be read", () -> "answer")).isEqualTo("answer");
	}

	@Test
	void failWithoutRetryOnTheStatusTellsWhatFailedAndTheAnswer() {
		final var problem = new ClientProblem(NOT_FOUND, "party-assets error: {detail=Asset 199001012385 not found, status=404 Not Found, title=Not Found}");

		assertThatThrownBy(() -> failWithoutRetryOn(NOT_FOUND, "Asset 'id' cannot be read", () -> {
			throw problem;
		}))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Asset 'id' cannot be read: ClientProblem 404 from party-assets (remote 404 Not Found)")
			.hasCause(problem);
	}

	@Test
	void failWithoutRetryOnPassesAnyOtherFailureAsItIs() {
		final var otherStatus = new ClientProblem(BAD_REQUEST, "Bad Request");
		final var serverProblem = new ServerProblem(BAD_GATEWAY, "Bad Gateway");

		assertThatThrownBy(() -> failWithoutRetryOn(NOT_FOUND, "Asset 'id' cannot be read", () -> {
			throw otherStatus;
		})).isSameAs(otherStatus);
		assertThatThrownBy(() -> failWithoutRetryOn(NOT_FOUND, "Asset 'id' cannot be read", () -> {
			throw serverProblem;
		})).isSameAs(serverProblem);
	}
}
