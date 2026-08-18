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
import se.sundsvall.alkt.api.model.StartProcessResponse;
import se.sundsvall.alkt.service.ProcessService;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@AutoConfigureWebTestClient
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("junit")
class ProcessResourceTest {

	static final String PATH = "/2281/ALKT";

	@MockitoBean
	private ProcessService processServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	@LocalServerPort
	private int port;

	@Test
	void startProcess() {

		// Arrange
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var errandId = randomUUID().toString();
		final var uuid = randomUUID().toString();

		when(processServiceMock.startProcess(any(), any(), any())).thenReturn(uuid);

		// Act
		final var response = webTestClient.post().uri(PATH + "/process/start/" + errandId)
			.exchange()
			.expectStatus().isAccepted()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody(StartProcessResponse.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response.getProcessId()).isEqualTo(uuid);
		verify(processServiceMock).startProcess(municipalityId, namespace, errandId);
		verifyNoMoreInteractions(processServiceMock);
	}

	@Test
	void updateProcess() {

		// Arrange
		final var municipalityId = "2281";
		final var namespace = "ALKT";
		final var uuid = randomUUID().toString();

		when(processServiceMock.startProcess(any(), any(), any())).thenReturn(uuid);

		// Act
		webTestClient.post().uri(PATH + "/process/update/" + uuid)
			.exchange()
			.expectStatus().isAccepted()
			.expectBody().isEmpty();

		// Assert
		verify(processServiceMock).updateProcess(municipalityId, namespace, uuid);
		verifyNoMoreInteractions(processServiceMock);
	}
}
