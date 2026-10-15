package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES_CHANGE;

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

	@Test
	void keysWhere() {
		assertThat(ProcessType.keysWhere(process -> process.getChangeDraftTitle().isPresent()))
			.containsExactlyInAnyOrder(ALCOHOL_SERVING_CHANGE.getKey(), TOBACCO_SALES_CHANGE.getKey());
	}
}
