package se.sundsvall.alkt.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessActivityTest {

	@Test
	void carriesTheActivityIdAndName() {
		final var activity = new ProcessActivity("activityId", "activityName");

		assertThat(activity.activityId()).isEqualTo("activityId");
		assertThat(activity.activityName()).isEqualTo("activityName");
	}
}
