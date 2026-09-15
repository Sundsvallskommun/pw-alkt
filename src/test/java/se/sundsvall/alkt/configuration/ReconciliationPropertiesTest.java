package se.sundsvall.alkt.configuration;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class ReconciliationPropertiesTest {

	@Autowired
	private ReconciliationProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.lookback()).isEqualTo(Duration.ofHours(24));
	}
}
