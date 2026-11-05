package se.sundsvall.alkt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.configuration.PermitJsonProperties.Settings;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class PermitJsonPropertiesTest {

	@Autowired
	private PermitJsonProperties properties;

	@Test
	void eachSchemaHasItsVersionAndTheKeyOfItsDecision() {
		assertThat(properties.of(ALCOHOL)).isEqualTo(new Settings("0.1", "aot_alcohol_decision"));
		assertThat(properties.of(TOBACCO)).isEqualTo(new Settings("0.1", "aot_tobacco_decision"));
	}
}
