package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;

class ProcessTypeTest {

	@Test
	void fromKey() {
		Arrays.stream(ProcessType.values()).forEach(process -> assertThat(ProcessType.fromKey(process.getKey())).contains(process));
	}

	@Test
	void fromKeyIsEmptyForAnUnknownKey() {
		assertThat(ProcessType.fromKey("process-reconciliation")).isEmpty();
		assertThat(ProcessType.fromKey(null)).isEmpty();
	}

	@Test
	void keys() {
		assertThat(ProcessType.keys())
			.hasSize(ProcessType.values().length)
			.contains(ALCOHOL_SERVING.getKey());
	}
}
