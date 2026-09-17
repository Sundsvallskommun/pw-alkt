package se.sundsvall.alkt.integration.partyassets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.problem.Problem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CREATED;
import static se.sundsvall.alkt.integration.partyassets.PartyAssetsIntegration.assetIdOf;

class PartyAssetsIntegrationTest {

	@Test
	void assetIdOfReadsTheLastSegmentOfLocation() {
		final var assetId = randomUUID().toString();

		assertThat(assetIdOf(created("https://party-assets.example.com/2281/assets/" + assetId))).isEqualTo(assetId);
	}

	/** Location is the only receipt on a create, so without it we do not know what to hang the attachments on. */
	@Test
	void assetIdOfFailsWhenTheAnswerCarriesNoLocation() {
		assertThatThrownBy(() -> assetIdOf(ResponseEntity.status(CREATED).build()))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	@Test
	void assetIdOfFailsOnALocationThatEndsInNothing() {
		assertThatThrownBy(() -> assetIdOf(created("https://party-assets.example.com/2281/assets/")))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
