package se.sundsvall.alkt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class ActionErrandPropertiesTest {

	@Autowired
	private ActionErrandProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.labelId()).isEqualTo("a8561314-5335-40db-80a9-b7f426a4baa2");
	}
}
