package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.util.Objects;
import se.sundsvall.alkt.service.model.PermitSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findCaseNumber;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toDecidedOn;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toStakeholderName;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

/**
 * Builds the JSON parameter of a notification from the form of its errand, its permit holder and the decision the
 * process made. Only what the schema of the asset has is taken, and a field without a value is left out.
 */
public final class NotificationJsonMapper {

	private static final String YES = "YES";
	private static final String STREET = "street";
	private static final String POSTAL_CODE = "postalCode";
	private static final String CITY = "city";
	private static final String ORGANIZATION_NUMBER = "organizationNumber";
	private static final String INDEFINITE = "INDEFINITE";
	private static final String TEMPORARY = "TEMPORARY";
	private static final String DISTANCE_SELLING = "DISTANCE_SELLING";

	private NotificationJsonMapper() {}

	public static ObjectNode toNotification(final PermitSchema schema, final JsonNode form, final Errand errand, final Stakeholder permitHolder, final String errandId,
		final String caseRegistrationNumber, final Decision decision) {
		final var notification = JsonNodeFactory.instance.objectNode();
		putText(notification, "errandId", errandId);
		putText(notification, "caseRegistrationNumber", caseRegistrationNumber);
		putText(notification, "externalCaseRegistrationNumber", findCaseNumber(errand).orElse(null));
		putObject(notification, "permitHolder", toPermitHolder(permitHolder));
		final var municipalityId = toPremisesMunicipalityId(errand);
		if (schema == TOBACCO) {
			putObject(notification, "pointOfSale", toPlace("pointOfSale", form, permitHolder, municipalityId));
			putObject(notification, "salesPeriod", toSalesPeriod(form));
			if (isYes(form.path("includesDistanceSelling"))) {
				notification.putArray("salesTypes").add(DISTANCE_SELLING);
			}
		} else {
			putObject(notification, "premises", toPlace("premises", form, permitHolder, municipalityId));
		}
		putObject(notification, "decision", toDecision(decision, caseRegistrationNumber));
		return notification;
	}

	private static ObjectNode toSalesPeriod(final JsonNode form) {
		final var salesPeriod = JsonNodeFactory.instance.objectNode();
		final var period = form.path("salesPeriod").asString(null);
		putText(salesPeriod, "period", period);
		if (INDEFINITE.equals(period)) {
			putText(salesPeriod, "from", form.path("salesStartDate"));
		} else if (TEMPORARY.equals(period)) {
			putText(salesPeriod, "from", form.path("salesPeriodDates").path("fromDate"));
			putText(salesPeriod, "to", form.path("salesPeriodDates").path("toDate"));
		}
		return salesPeriod;
	}

	private static ObjectNode toPermitHolder(final Stakeholder stakeholder) {
		final var holder = JsonNodeFactory.instance.objectNode();
		putText(holder, "name", toStakeholderName(stakeholder));
		putText(holder, ORGANIZATION_NUMBER, toParameterValues(stakeholder.getParameters()).get(ORGANIZATION_NUMBER));
		putObject(holder, "address", toStakeholderAddress(stakeholder));
		return holder;
	}

	private static ObjectNode toPlace(final String prefix, final JsonNode form, final Stakeholder permitHolder, final String municipalityId) {
		final var place = JsonNodeFactory.instance.objectNode();
		putText(place, "name", form.path(prefix + "Name"));
		putObject(place, "address", isYes(form.path(prefix + "AddressSameAsCompany")) ? toStakeholderAddress(permitHolder) : toFormAddress(form.path(prefix + "Address")));
		final var contact = form.path(prefix + "Contact");
		putText(place, "phoneNumber", contact.path("phoneNumber"));
		putText(place, "emailAddress", contact.path("emailAddress"));
		putText(place, "municipalityId", municipalityId);
		return place;
	}

	private static ObjectNode toFormAddress(final JsonNode formAddress) {
		final var address = JsonNodeFactory.instance.objectNode();
		putText(address, STREET, formAddress.path(STREET));
		putText(address, POSTAL_CODE, formAddress.path(POSTAL_CODE));
		putText(address, CITY, formAddress.path(CITY));
		return address;
	}

	private static ObjectNode toStakeholderAddress(final Stakeholder stakeholder) {
		final var address = JsonNodeFactory.instance.objectNode();
		putText(address, "careOf", stakeholder.getCareOf());
		putText(address, STREET, stakeholder.getAddress());
		putText(address, POSTAL_CODE, stakeholder.getZipCode());
		putText(address, CITY, stakeholder.getCity());
		return address;
	}

	private static ObjectNode toDecision(final Decision decision, final String caseRegistrationNumber) {
		final var result = JsonNodeFactory.instance.objectNode();
		putText(result, "caseRegistrationNumber", caseRegistrationNumber);
		putText(result, "outcome", decision.getOutcome());
		putText(result, "decisionDate", toDecidedOn(decision).map(LocalDate::toString).orElse(null));
		putText(result, "validFrom", Objects.toString(decision.getValidFrom(), null));
		putText(result, "legalBasis", decision.getLegalBasis());
		putText(result, "delegationReference", decision.getDelegationReference());
		return result;
	}

	private static boolean isYes(final JsonNode answer) {
		return YES.equals(answer.asString(null));
	}

	private static void putText(final ObjectNode target, final String name, final JsonNode value) {
		if (value.isString()) {
			putText(target, name, value.asString());
		}
	}

	private static void putText(final ObjectNode target, final String name, final String value) {
		if (isNotBlank(value)) {
			target.put(name, value);
		}
	}

	private static void putObject(final ObjectNode target, final String name, final ObjectNode value) {
		if (!value.isEmpty()) {
			target.set(name, value);
		}
	}
}
