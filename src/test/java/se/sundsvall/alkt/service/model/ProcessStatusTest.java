package se.sundsvall.alkt.service.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessStatusTest {

	@ParameterizedTest
	@EnumSource(names = {
		"COMPLETED", "FAILED"
	})
	void aStateTheInstanceDoesNotLeaveIsTerminal(final ProcessStatus status) {
		assertThat(status.isTerminal()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(names = {
		"RUNNING", "WAITING", "RETRYING"
	})
	void aStateTheInstanceMovesOnFromIsNot(final ProcessStatus status) {
		assertThat(status.isTerminal()).isFalse();
	}
}
