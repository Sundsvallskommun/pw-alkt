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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

@AutoConfigureWebTestClient
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("junit")
class ProcessResourceTest {

	static final String PATH = "/2281/ALKT/process/errand-events";

	@MockitoBean
	private ProcessService processServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void handleErrandEvent() {

		// Arrange
		final var errandId = randomUUID().toString();
		final var errandEvent = new ErrandEvent();
		errandEvent.setEventId(randomUUID().toString());
		errandEvent.setEventType(UPDATE);
		errandEvent.setEventSubType("MESSAGE");
		errandEvent.setErrandId(errandId);
		errandEvent.setProcessKey("alcohol-serving");
		errandEvent.setStartAllowed(false);

		// Act
		webTestClient.post().uri(PATH)
			.bodyValue(errandEvent)
			.exchange()
			.expectStatus().isAccepted()
			.expectBody().isEmpty();

		// Assert
		verify(processServiceMock).handleErrandEvent("2281", "ALKT", errandEvent);
		verifyNoMoreInteractions(processServiceMock);
	}

	/**
	 * Omitting the permission is valid and reads as false: a publisher that omits it should leave the errand waiting to
	 * be started by hand, not produce a delivery that keeps being retried.
	 */
	@Test
	void acceptsAnEventWithoutTheStartPermission() {

		// Arrange
		final var errandEvent = new ErrandEvent();
		errandEvent.setEventId(randomUUID().toString());
		errandEvent.setEventType(UPDATE);
		errandEvent.setErrandId(randomUUID().toString());

		// Act
		webTestClient.post().uri(PATH)
			.bodyValue(errandEvent)
			.exchange()
			.expectStatus().isAccepted();

		// Assert
		assertThat(errandEvent.isStartAllowed()).isFalse();
		verify(processServiceMock).handleErrandEvent("2281", "ALKT", errandEvent);
	}
}
