package se.sundsvall.alkt.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import se.sundsvall.dept44.problem.Problem;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CREATED;
import static se.sundsvall.alkt.util.ResponseUtil.getIdOfCreatedResource;

class ResponseUtilTest {

	private static final String SERVICE = "Some service";

	@Test
	void getIdOfCreatedResourceReadsTheLastSegmentOfLocation() {
		final var resourceId = randomUUID().toString();

		assertThat(getIdOfCreatedResource(created("https://some-service.example.com/2281/resources/" + resourceId), SERVICE)).isEqualTo(resourceId);
	}

	/** Support Management and party-assets both answer with a path rather than an absolute URL. */
	@Test
	void getIdOfCreatedResourceReadsARelativeLocation() {
		final var resourceId = randomUUID().toString();

		assertThat(getIdOfCreatedResource(created("/2281/resources/" + resourceId), SERVICE)).isEqualTo(resourceId);
	}

	/** Location is the only receipt on a create, so without it we do not know what was created. */
	@Test
	void getIdOfCreatedResourceFailsWhenTheAnswerCarriesNoLocation() {
		assertThatThrownBy(() -> getIdOfCreatedResource(ResponseEntity.status(CREATED).build(), SERVICE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Some service created a resource without saying which");
	}

	@Test
	void getIdOfCreatedResourceFailsOnALocationThatEndsInNothing() {
		assertThatThrownBy(() -> getIdOfCreatedResource(created("https://some-service.example.com/2281/resources/"), SERVICE))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("without saying which");
	}

	private static ResponseEntity<Void> created(final String location) {
		final var headers = new HttpHeaders();
		headers.add(HttpHeaders.LOCATION, location);
		return ResponseEntity.status(CREATED).headers(headers).build();
	}
}
