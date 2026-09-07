package se.sundsvall.alkt.api.model;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

class ErrandEventTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> OffsetDateTime.now().plusNanos(java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)), OffsetDateTime.class);
	}

	@Test
	void testBean() {
		assertThat(ErrandEvent.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(new ErrandEvent()).hasAllNullFieldsOrProperties();
	}

	@Test
	void testFields() {
		final var eventId = randomUUID().toString();
		final var errandId = randomUUID().toString();
		final var occurredAt = OffsetDateTime.now();

		final var bean = new ErrandEvent();
		bean.setEventId(eventId);
		bean.setEventType(UPDATE);
		bean.setEventSubType("SIGNAL");
		bean.setErrandId(errandId);
		bean.setProcessKey("alcohol-serving");
		bean.setStartAllowed(true);
		bean.setSignalName("review_completed");
		bean.setOccurredAt(occurredAt);

		assertThat(bean).isNotNull().hasNoNullFieldsOrProperties();
		assertThat(bean.getEventId()).isEqualTo(eventId);
		assertThat(bean.getEventType()).isEqualTo(UPDATE);
		assertThat(bean.getEventSubType()).isEqualTo("SIGNAL");
		assertThat(bean.getErrandId()).isEqualTo(errandId);
		assertThat(bean.getProcessKey()).isEqualTo("alcohol-serving");
		assertThat(bean.getStartAllowed()).isTrue();
		assertThat(bean.permitsStart()).isTrue();
		assertThat(bean.getSignalName()).isEqualTo("review_completed");
		assertThat(bean.getOccurredAt()).isEqualTo(occurredAt);
	}

	/** An absent permission reads as no permission, since a process started in error cannot be taken back. */
	@Test
	void startAllowedDefaultsToFalseWhenAbsent() {
		assertThat(new ErrandEvent().permitsStart()).isFalse();
	}
}
