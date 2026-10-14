package se.sundsvall.alkt.api.validation.impl;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.api.model.ErrandEvent.EventType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.DELETE;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.UPDATE;

@ExtendWith(MockitoExtension.class)
class ValidProcessKeyConstraintValidatorTest {

	@Mock
	private ConstraintValidatorContext contextMock;

	@Mock
	private ConstraintViolationBuilder builderMock;

	@Mock
	private NodeBuilderCustomizableContext nodeBuilderMock;

	private final ValidProcessKeyConstraintValidator validator = new ValidProcessKeyConstraintValidator();

	private static ErrandEvent event(final EventType eventType, final String processKey) {
		final var errandEvent = new ErrandEvent();
		errandEvent.setEventType(eventType);
		errandEvent.setProcessKey(processKey);
		return errandEvent;
	}

	@ParameterizedTest
	@EnumSource(EventType.class)
	void aKnownKeyIsValid(final EventType eventType) {
		assertThat(validator.isValid(event(eventType, "alcohol-serving"), contextMock)).isTrue();
		verifyNoInteractions(contextMock);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void anErrandWithoutAKeyIsValid(final String processKey) {
		assertThat(validator.isValid(event(UPDATE, processKey), contextMock)).isTrue();
		verifyNoInteractions(contextMock);
	}

	@Test
	void aDeleteWithAnUnknownKeyIsValid() {
		assertThat(validator.isValid(event(DELETE, "no-such-process"), contextMock)).isTrue();
		verifyNoInteractions(contextMock);
	}

	@Test
	void aMissingEventIsValid() {
		assertThat(validator.isValid(null, contextMock)).isTrue();
		verifyNoInteractions(contextMock);
	}

	@ParameterizedTest
	@EnumSource(value = EventType.class, names = "DELETE", mode = EnumSource.Mode.EXCLUDE)
	void anUnknownKeyIsInvalidOnTheProcessKey(final EventType eventType) {
		when(contextMock.getDefaultConstraintMessageTemplate()).thenReturn("not a process key this service knows");
		when(contextMock.buildConstraintViolationWithTemplate("not a process key this service knows")).thenReturn(builderMock);
		when(builderMock.addPropertyNode("processKey")).thenReturn(nodeBuilderMock);

		assertThat(validator.isValid(event(eventType, "no-such-process"), contextMock)).isFalse();

		verify(contextMock).disableDefaultConstraintViolation();
		verify(nodeBuilderMock).addConstraintViolation();
	}

	@Test
	void anUnknownKeyWithoutAnEventTypeIsInvalid() {
		when(contextMock.getDefaultConstraintMessageTemplate()).thenReturn("not a process key this service knows");
		when(contextMock.buildConstraintViolationWithTemplate("not a process key this service knows")).thenReturn(builderMock);
		when(builderMock.addPropertyNode("processKey")).thenReturn(nodeBuilderMock);

		assertThat(validator.isValid(event(null, "no-such-process"), contextMock)).isFalse();

		verify(contextMock).disableDefaultConstraintViolation();
		verify(nodeBuilderMock).addConstraintViolation();
	}
}
