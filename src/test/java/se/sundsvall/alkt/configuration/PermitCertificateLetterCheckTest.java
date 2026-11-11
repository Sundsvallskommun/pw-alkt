package se.sundsvall.alkt.configuration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.Letter;
import se.sundsvall.alkt.configuration.PermitCertificateProperties.SupportInfo;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermitCertificateLetterCheckTest {

	private static final SupportInfo SUPPORT_INFO = new SupportInfo("Kontakta alkohol- och tobaksenheten", "alkohol.tobak@sundsvall.se", "060-19 10 00", "https://sundsvall.se/alkohol");

	private final PathMatchingResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();

	@Test
	void startsWhenEveryProcessWithTheStepHasALetter() {
		final var properties = new PermitCertificateProperties("2120002411", "KSK(Alkohol och tobak)", SUPPORT_INFO, Map.of(
			"alcohol-serving", new Letter("Bevis om serveringstillstånd", "Här kommer beviset på ditt serveringstillstånd."),
			"alcohol-serving-addition", new Letter("Bevis om tillägg till serveringstillstånd", "Här kommer beviset på tillägget till ditt serveringstillstånd."),
			"alcohol-serving-change", new Letter("Bevis om ändrat serveringstillstånd", "Här kommer beviset på ditt ändrade serveringstillstånd."),
			"tobacco-sales", new Letter("Bevis om tobakstillstånd", "Här kommer beviset på ditt tillstånd att sälja tobak."),
			"tobacco-sales-change", new Letter("Bevis om ändrat tobakstillstånd", "Här kommer beviset på ditt ändrade tillstånd att sälja tobak.")));

		assertThatNoException().isThrownBy(() -> new PermitCertificateLetterCheck(properties, patternResolver));
	}

	@Test
	void refusesToStartWhenAProcessWithTheStepHasNoLetter() {
		final var properties = new PermitCertificateProperties("2120002411", "KSK(Alkohol och tobak)", SUPPORT_INFO, Map.of(
			"alcohol-serving", new Letter("Bevis om serveringstillstånd", "Här kommer beviset på ditt serveringstillstånd."),
			"alcohol-serving-addition", new Letter("Bevis om tillägg till serveringstillstånd", "Här kommer beviset på tillägget till ditt serveringstillstånd."),
			"alcohol-serving-change", new Letter("Bevis om ändrat serveringstillstånd", "Här kommer beviset på ditt ändrade serveringstillstånd."),
			"tobacco-sales", new Letter("Bevis om tobakstillstånd", "Här kommer beviset på ditt tillstånd att sälja tobak.")));

		assertThatThrownBy(() -> new PermitCertificateLetterCheck(properties, patternResolver))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No letter under permit-certificate.letters for the processes [tobacco-sales-change], which send the permit certificate");
	}
}
