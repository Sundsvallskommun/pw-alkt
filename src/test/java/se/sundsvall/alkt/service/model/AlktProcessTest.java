package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.AlktProcess.ALCOHOL_SERVING;
import static se.sundsvall.alkt.service.model.AlktProcess.ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.service.model.AlktProcess.TOBACCO_SALES_CHANGE;

class AlktProcessTest {

	@Test
	void fromKey() {
		Arrays.stream(AlktProcess.values()).forEach(process -> assertThat(AlktProcess.fromKey(process.key())).contains(process));
	}

	@Test
	void fromKeyIsEmptyForAnUnknownKey() {
		assertThat(AlktProcess.fromKey("process-reconciliation")).isEmpty();
		assertThat(AlktProcess.fromKey(null)).isEmpty();
	}

	@Test
	void keys() {
		assertThat(AlktProcess.keys())
			.hasSize(AlktProcess.values().length)
			.contains(ALCOHOL_SERVING.key());
	}

	@Test
	void keysWhere() {
		assertThat(AlktProcess.keysWhere(process -> process.changeDraftTitle().isPresent()))
			.containsExactlyInAnyOrder(ALCOHOL_SERVING_CHANGE.key(), TOBACCO_SALES_CHANGE.key());
	}
}
