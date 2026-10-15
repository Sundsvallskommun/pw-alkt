package se.sundsvall.alkt.api.validation.impl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import se.sundsvall.alkt.api.model.ErrandEvent;
import se.sundsvall.alkt.api.validation.ValidProcessKey;
import se.sundsvall.alkt.service.model.ProcessType;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static se.sundsvall.alkt.api.model.ErrandEvent.EventType.DELETE;

public class ValidProcessKeyConstraintValidator implements ConstraintValidator<ValidProcessKey, ErrandEvent> {

	private static final String VALIDATED_NODE = "processKey";

	// Why: a delete removes every instance of the errand whatever its key, so a label that changed key must not keep the
	// instances of a deleted errand alive.
	@Override
	public boolean isValid(final ErrandEvent errandEvent, final ConstraintValidatorContext context) {
		if (errandEvent == null || errandEvent.getEventType() == DELETE || isBlank(errandEvent.getProcessKey())
			|| ProcessType.fromKey(errandEvent.getProcessKey()).isPresent()) {
			return true;
		}

		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			.addPropertyNode(VALIDATED_NODE)
			.addConstraintViolation();
		return false;
	}
}
