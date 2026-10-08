package se.sundsvall.alkt.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swedish time, as the days of a permit are Swedish days. */
@Configuration
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Europe/Stockholm"));
	}
}
