package se.sundsvall.alkt.service.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class ProcessStatusTest {

	@ParameterizedTest
	@EnumSource(names = {
		"COMPLETED", "FAILED"
	})
	void aStateWithNoWaitStateAfterItIsTerminal(final ProcessStatus status) {
		assertThat(status.isTerminal()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(names = {
		"RUNNING", "WAITING", "RETRYING"
	})
	void aStateTheInstanceMovesOnFromIsNot(final ProcessStatus status) {
		assertThat(status.isTerminal()).isFalse();
	}

	/** An incident leaves the instance listening, so FAILED takes signals too. */
	@ParameterizedTest
	@EnumSource(names = "COMPLETED", mode = EXCLUDE)
	void everyStateButCompletedTakesSignals(final ProcessStatus status) {
		assertThat(status.takesSignals()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(names = "COMPLETED")
	void aCompletedProcessTakesNoSignal(final ProcessStatus status) {
		assertThat(status.takesSignals()).isFalse();
	}
}
