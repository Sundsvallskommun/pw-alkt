package se.sundsvall.alkt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.service.ProcessService;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

@AutoConfigureWebTestClient
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("junit")
class ProcessResourceFailuresTest {

	@MockitoBean
	private ProcessService processServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	private static ErrandEvent validEvent() {
		final var errandEvent = new ErrandEvent();
		errandEvent.setEventId(randomUUID().toString());
		errandEvent.setEventType(UPDATE);
		errandEvent.setErrandId(randomUUID().toString());
		errandEvent.setStartAllowed(false);
		return errandEvent;
	}

	private ConstraintViolationProblem post(final String path, final ErrandEvent errandEvent) {
		return webTestClient.post().uri(path)
			.bodyValue(errandEvent)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();
	}

	/** The errand id is Support Management's errand identifier, a UUID string - not a numeric case number. */
	@Test
	void errandIdIsNotUUID() {

		// Arrange
		final var errandEvent = validEvent();
		errandEvent.setErrandId("invalid");

		// Act
		final var response = post("/2281/ALKT/process/errand-events", errandEvent);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("errandId", "not a valid UUID"));
		verifyNoInteractions(processServiceMock);
	}

	@Test
	void eventIdIsMissing() {

		// Arrange
		final var errandEvent = validEvent();
		errandEvent.setEventId(null);

		// Act
		final var response = post("/2281/ALKT/process/errand-events", errandEvent);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field)
			.containsExactly("eventId");
		verifyNoInteractions(processServiceMock);
	}

	@Test
	void eventTypeIsMissing() {

		// Arrange
		final var errandEvent = validEvent();
		errandEvent.setEventType(null);

		// Act
		final var response = post("/2281/ALKT/process/errand-events", errandEvent);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field)
			.containsExactly("eventType");
		verifyNoInteractions(processServiceMock);
	}

	@Test
	void invalidNamespace() {

		// Act
		final var response = post("/2281/SBK.ALKT/process/errand-events", validEvent());

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("handleErrandEvent.namespace", "not a valid namespace. Must be 2-32 characters and can only contain A-Z, a-z, 0-9, - and _"));
		verifyNoInteractions(processServiceMock);
	}
}
