package se.sundsvall.alkt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.service.ProcessService;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@AutoConfigureWebTestClient
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("junit")
class ProcessResourceFailuresTest {

	@MockitoBean
	private ProcessService processServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	@LocalServerPort
	private int port;

	/**
	 * The errand id is Support Management's errand identifier, a UUID string - not a numeric case number.
	 */
	@Test
	void startProcessInvalidErrandIdIsNotUUID() {

		// Arrange
		final var errandId = "invalid";

		// Act
		final var response = webTestClient.post().uri("/2281/ALKT/process/start/" + errandId)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("startProcess.errandId", "not a valid UUID"));

		verifyNoInteractions(processServiceMock);
	}

	@Test
	void startProcessInvalidNamespace() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var namespace = "SBK.ALKT";

		// Act
		final var response = webTestClient.post().uri("/2281/" + namespace + "/process/start/" + errandId)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("startProcess.namespace", "not a valid namespace. Must be 2-32 characters and can only contain A-Z, a-z, 0-9, - and _"));

		verifyNoInteractions(processServiceMock);
	}

	@Test
	void updateProcessInvalidProcessInstanceIdIsNotUUID() {

		// Arrange
		final var processInstanceId = "invalid";

		// Act
		final var response = webTestClient.post().uri("/2281/ALKT/process/update/" + processInstanceId)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("updateProcess.processInstanceId", "not a valid UUID"));

		verifyNoInteractions(processServiceMock);
	}
}
