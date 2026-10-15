package se.sundsvall.alkt.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import se.sundsvall.alkt.api.validation.impl.ValidProcessKeyConstraintValidator;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** The process key of an errand event names a process of this service, unless the event is a delete. */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = ValidProcessKeyConstraintValidator.class)
public @interface ValidProcessKey {

	String message() default "not a process key this service knows";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
