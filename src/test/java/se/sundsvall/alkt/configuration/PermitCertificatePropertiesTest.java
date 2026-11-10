package se.sundsvall.alkt.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.SupportInfo;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class PermitCertificatePropertiesTest {

	@Autowired
	private PermitCertificateProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.organizationNumber()).isEqualTo("2120002411");
		assertThat(properties.department()).isEqualTo("KSK(Alkohol och tobak)");
		assertThat(properties.supportInfo()).isEqualTo(new SupportInfo("Har du frågor om ditt tillstånd är du välkommen att kontakta alkohol- och tobaksenheten",
			"alkohol.tobak@sundsvall.se", "060-19 10 00", "https://sundsvall.se/alkohol-och-tobak"));
		assertThat(properties.letters()).containsOnlyKeys("alcohol-serving", "alcohol-serving-addition", "alcohol-serving-change", "tobacco-sales", "tobacco-sales-change");
		assertThat(properties.letters().get("alcohol-serving")).isEqualTo(new Letter("Bevis om serveringstillstånd",
			"Här kommer beviset på ditt serveringstillstånd. Beslutet finns i ditt ärende på Mina sidor."));
	}
}
