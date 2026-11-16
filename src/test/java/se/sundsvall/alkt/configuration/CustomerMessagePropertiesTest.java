package se.sundsvall.alkt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class CustomerMessagePropertiesTest {

	@Autowired
	private CustomerMessageProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.texts()).containsOnly(
			entry("processing-started",
				"Vi har nu börjat handlägga ditt ärende. Om vi behöver mer information från dig kontaktar vi dig. Det här meddelandet är skickat automatiskt och behöver inte besvaras."),
			entry("notification-received",
				"Vi har tagit emot din anmälan. Du får ett nytt meddelande när anmälan är registrerad. Det här meddelandet är skickat automatiskt och behöver inte besvaras."),
			entry("notification-registered",
				"Din anmälan är nu registrerad. Om vi behöver mer information från dig kontaktar vi dig. Det här meddelandet är skickat automatiskt och behöver inte besvaras."));
	}
}
