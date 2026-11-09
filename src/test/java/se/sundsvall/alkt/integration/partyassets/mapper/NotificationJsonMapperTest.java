package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.partyassets.mapper.NotificationJsonMapper.toNotification;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

class NotificationJsonMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String ERRAND_ID = "c30a6f18-9d54-4b72-8e05-2f1a7c4b6d98";
	private static final String ERRAND_NUMBER = "ALKT-2026-000123";

	@Test
	void toNotificationTakesThePremisesFromTheFormAndTheHolderFromThePrimaryStakeholder() {
		final var form = json("""
			{
			  "premisesName": "Kafé Solsidan",
			  "premisesAddressSameAsCompany": "NO",
			  "premisesAddress": { "street": "Badhusparken 2", "postalCode": "85231", "city": "Sundsvall" },
			  "premisesContact": { "phoneNumber": "060-15 40 20", "emailAddress": "info@solsidan.se" },
			  "invoiceRecipient": { "street": "Box 100", "postalCode": "85102", "city": "Sundsvall" },
			  "selfMonitoringProgram": "YES",
			  "isFoodRegistered": "YES"
			}
			""");

		final var result = toNotification(ALCOHOL, form, errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result).isEqualTo(json("""
			{
			  "errandId": "c30a6f18-9d54-4b72-8e05-2f1a7c4b6d98",
			  "caseRegistrationNumber": "ALKT-2026-000123",
			  "permitHolder": {
			    "name": "Solsidan Kafé AB",
			    "organizationNumber": "556612-4144",
			    "address": { "careOf": "c/o Ekonomi", "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" }
			  },
			  "premises": {
			    "name": "Kafé Solsidan",
			    "address": { "street": "Badhusparken 2", "postalCode": "85231", "city": "Sundsvall" },
			    "phoneNumber": "060-15 40 20",
			    "emailAddress": "info@solsidan.se",
			    "municipalityId": "2281"
			  },
			  "decision": {
			    "caseRegistrationNumber": "ALKT-2026-000123",
			    "outcome": "APPROVAL",
			    "decisionDate": "2026-10-05",
			    "validFrom": "2026-10-06",
			    "legalBasis": "8 kap. 8 § alkohollagen (2010:1622)",
			    "delegationReference": "Delegationsordning 4.1"
			  }
			}
			"""));
	}

	@Test
	void toNotificationTakesTheAddressOfThePremisesFromTheHolderWhenTheFormSaysItIsTheSame() {
		final var form = json("""
			{
			  "premisesName": "Kafé Solsidan",
			  "premisesAddressSameAsCompany": "YES",
			  "premisesAddress": { "street": "Gammal adress 1", "postalCode": "85000", "city": "Sundsvall" }
			}
			""");

		final var result = toNotification(ALCOHOL, form, errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("premises").path("address")).isEqualTo(json("""
			{ "careOf": "c/o Ekonomi", "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" }
			"""));
	}

	@Test
	void toNotificationTakesTheMunicipalityOfThePremisesFromTheErrand() {
		final var result = toNotification(ALCOHOL, json("{}"), errand("2262"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("premises").path("municipalityId").asString()).isEqualTo("2262");
	}

	@Test
	void toNotificationLeavesOutTheLegalBasisAndDelegationReferenceOfADecisionWithoutThem() {
		final var decision = new Decision()
			.decidedAt(OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.UTC))
			.validFrom(LocalDate.of(2026, 10, 6));

		final var result = toNotification(ALCOHOL, json("{}"), errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision);

		assertThat(result.path("decision")).isEqualTo(json("""
			{ "caseRegistrationNumber": "ALKT-2026-000123", "decisionDate": "2026-10-05", "validFrom": "2026-10-06" }
			"""));
	}

	@Test
	void toNotificationNamesAPermitHolderWithoutOrganizationNameByTheNameOfThePerson() {
		final var holder = new Stakeholder().role("PRIMARY").externalId("8f3a2b1c-4d5e-4f60-9a7b-8c9d0e1f2a3b").firstName("Maja").lastName("Lind");

		final var result = toNotification(ALCOHOL, json("{}"), errand("2281"), holder, ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("permitHolder").path("name").asString()).isEqualTo("Maja Lind");
	}

	@Test
	void toNotificationLeavesOutWhatTheFormAndTheHolderDoNotGive() {
		final var holder = new Stakeholder().role("PRIMARY").externalId("8f3a2b1c-4d5e-4f60-9a7b-8c9d0e1f2a3b");

		final var result = toNotification(ALCOHOL, json("{}"), errand("2281"), holder, ERRAND_ID, ERRAND_NUMBER, new Decision());

		assertThat(result).isEqualTo(json("""
			{
			  "errandId": "c30a6f18-9d54-4b72-8e05-2f1a7c4b6d98",
			  "caseRegistrationNumber": "ALKT-2026-000123",
			  "premises": { "municipalityId": "2281" },
			  "decision": { "caseRegistrationNumber": "ALKT-2026-000123" }
			}
			"""));
	}

	@Test
	void toNotificationTakesTheExternalCaseRegistrationNumberFromTheErrand() {
		final var errand = new Errand().parameters(List.of(new Parameter().key("externalCaseRegistrationNumber").values(List.of("MBN 2026-123"))));

		final var result = toNotification(ALCOHOL, json("{}"), errand, permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("externalCaseRegistrationNumber").asString()).isEqualTo("MBN 2026-123");
	}

	@Test
	void toNotificationTakesThePointOfSaleAndTheIndefiniteSalesPeriodOfATobaccoForm() {
		final var form = json("""
			{
			  "pointOfSaleName": "Kiosken i Bergsåker",
			  "pointOfSaleAddressSameAsCompany": "NO",
			  "pointOfSaleAddress": { "street": "Bergsåkersvägen 1", "postalCode": "85640", "city": "Sundsvall" },
			  "pointOfSaleContact": { "phoneNumber": "060-11 22 33", "emailAddress": "kiosk@bergsaker.se" },
			  "premisesName": "Fel fält för tobak",
			  "salesPeriod": "INDEFINITE",
			  "salesStartDate": "2026-11-01",
			  "salesPeriodDates": { "fromDate": "2026-12-01", "toDate": "2027-01-31" },
			  "includesDistanceSelling": "YES",
			  "distanceSellingWebsite": "https://kiosken.se",
			  "invoiceRecipient": { "street": "Box 100", "postalCode": "85102", "city": "Sundsvall" },
			  "businessDescription": "Kiosk med tidningar",
			  "hasSupplierAgreement": "YES"
			}
			""");

		final var result = toNotification(TOBACCO, form, errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result).isEqualTo(json("""
			{
			  "errandId": "c30a6f18-9d54-4b72-8e05-2f1a7c4b6d98",
			  "caseRegistrationNumber": "ALKT-2026-000123",
			  "permitHolder": {
			    "name": "Solsidan Kafé AB",
			    "organizationNumber": "556612-4144",
			    "address": { "careOf": "c/o Ekonomi", "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" }
			  },
			  "pointOfSale": {
			    "name": "Kiosken i Bergsåker",
			    "address": { "street": "Bergsåkersvägen 1", "postalCode": "85640", "city": "Sundsvall" },
			    "phoneNumber": "060-11 22 33",
			    "emailAddress": "kiosk@bergsaker.se",
			    "municipalityId": "2281"
			  },
			  "salesPeriod": { "period": "INDEFINITE", "from": "2026-11-01" },
			  "salesTypes": ["DISTANCE_SELLING"],
			  "decision": {
			    "caseRegistrationNumber": "ALKT-2026-000123",
			    "outcome": "APPROVAL",
			    "decisionDate": "2026-10-05",
			    "validFrom": "2026-10-06",
			    "legalBasis": "8 kap. 8 § alkohollagen (2010:1622)",
			    "delegationReference": "Delegationsordning 4.1"
			  }
			}
			"""));
	}

	@Test
	void toNotificationTakesTheAddressOfThePointOfSaleFromTheHolderWhenTheFormSaysItIsTheSame() {
		final var form = json("""
			{ "pointOfSaleAddressSameAsCompany": "YES", "pointOfSaleAddress": { "street": "Gammal adress 1" } }
			""");

		final var result = toNotification(TOBACCO, form, errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("pointOfSale").path("address")).isEqualTo(json("""
			{ "careOf": "c/o Ekonomi", "street": "Storgatan 33", "postalCode": "852 30", "city": "Sundsvall" }
			"""));
	}

	@Test
	void toNotificationTakesTheDatesOfATemporarySalesPeriod() {
		final var form = json("""
			{ "salesPeriod": "TEMPORARY", "salesStartDate": "2026-11-01", "salesPeriodDates": { "fromDate": "2026-12-01", "toDate": "2027-01-31" } }
			""");

		final var result = toNotification(TOBACCO, form, errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.path("salesPeriod")).isEqualTo(json("""
			{ "period": "TEMPORARY", "from": "2026-12-01", "to": "2027-01-31" }
			"""));
	}

	@Test
	void toNotificationHasNoSalesTypesWithoutDistanceSelling() {
		final var result = toNotification(TOBACCO, json("""
			{ "includesDistanceSelling": "NO" }
			"""), errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.has("salesTypes")).isFalse();
	}

	@Test
	void toNotificationGivesAnAlcoholNotificationNoSalesPeriodOrSalesTypes() {
		final var result = toNotification(ALCOHOL, json("""
			{ "salesPeriod": "INDEFINITE", "salesStartDate": "2026-11-01", "includesDistanceSelling": "YES", "pointOfSaleName": "Kiosken i Bergsåker" }
			"""), errand("2281"), permitHolder(), ERRAND_ID, ERRAND_NUMBER, decision());

		assertThat(result.has("salesPeriod")).isFalse();
		assertThat(result.has("salesTypes")).isFalse();
		assertThat(result.has("pointOfSale")).isFalse();
	}

	private static Decision decision() {
		return new Decision()
			.outcome("APPROVAL")
			.terms(List.of(new DecisionTerm().sortOrder(1).text("Folköl ska stå skilt från lättdrycker.")))
			.decidedAt(OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.UTC))
			.validFrom(LocalDate.of(2026, 10, 6))
			.legalBasis("8 kap. 8 § alkohollagen (2010:1622)")
			.delegationReference("Delegationsordning 4.1");
	}

	private static Errand errand(final String premisesMunicipalityId) {
		return new Errand()
			.parameters(List.of(new Parameter().key("premisesMunicipalityId").values(List.of(premisesMunicipalityId))));
	}

	private static Stakeholder permitHolder() {
		return new Stakeholder().role("PRIMARY").externalId("8f3a2b1c-4d5e-4f60-9a7b-8c9d0e1f2a3b")
			.organizationName("Solsidan Kafé AB")
			.careOf("c/o Ekonomi")
			.address("Storgatan 33")
			.zipCode("852 30")
			.city("Sundsvall")
			.parameters(List.of(new Parameter().key("organizationNumber").values(List.of("556612-4144"))));
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
