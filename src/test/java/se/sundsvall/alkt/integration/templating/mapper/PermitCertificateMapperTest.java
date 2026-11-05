package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Decision;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.exception.NonRetryableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.sundsvall.alkt.integration.templating.mapper.PermitCertificateMapper.toCertificateParameters;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

class PermitCertificateMapperTest {

	/**
	 * What serving-permit-certificate reads, in Sundsvall and in Timrå alike, copied from the template in templating. A
	 * template that reads something new needs it added here.
	 */
	private static final String[] SERVING_TEMPLATE_PLACEHOLDERS = {
		"caseNumber", "conditions", "decisionDate", "decisionDateAndCaseNumber", "decisionMaker", "decisionText", "executionDate", "issuedByNameAndTitle", "issuedByUnit",
		"permitHolderName", "permitHolderOrgNumber", "permitHolderPhone", "permitHolderPostalAddress", "permitHolderStreet", "premisesName", "premisesPhone",
		"premisesPostalAddress", "premisesRestaurantNumber", "premisesStreet", "replacesDecision", "scopePeriodFromTo", "servingAreaDescription",
		"servingAreaDrawingReference", "servingAreaMaxPersons", "servingAreaSeats", "servingHours", "validFrom", "beverageBeer", "beverageOtherFermented",
		"beverageSpirits", "beverageWine", "scopeAllYear", "scopeAnnualPeriod", "scopeTasting", "servingToClosedCompany", "servingToPublic"
	};

	/**
	 * What tobacco-permit-certificate reads, in Sundsvall and in Timrå alike, copied from the template in templating. A
	 * template that reads something new needs it added here.
	 */
	private static final String[] TOBACCO_TEMPLATE_PLACEHOLDERS = {
		"caseNumber", "decisionDate", "decisionMaker", "decisionOfficerAndCaseNumber", "decisionText", "executionDate", "information", "issuedByNameAndTitle",
		"issuedByUnit", "permitHolderCareOf", "permitHolderName", "permitHolderOrgNumber", "permitHolderPostalAddress", "permitHolderStreet", "premisesName",
		"premisesPhone", "premisesPostalAddress", "premisesPropertyDesignation", "premisesStreet", "replacesDecision", "temporaryPeriod", "validFrom",
		"salesOnline", "salesRetail", "salesWholesale", "validityIndefinite", "validityTemporary"
	};

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Decision APPROVAL = new Decision().id("decision-id").outcome("APPROVAL");

	private static final String SERVING_PERMIT = """
		{
		  "errandId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
		  "caseRegistrationNumber": "ALKT-2026-000123",
		  "externalCaseRegistrationNumber": "2026/0456",
		  "servingPermitType": "PERMANENT_SERVING",
		  "permitHolder": {
		    "name": "Bolaget AB",
		    "organizationNumber": "556677-8899",
		    "address": { "careOf": "Ekonomiavdelningen", "street": "Box 1", "postalCode": "852 30", "city": "Sundsvall" },
		    "phoneNumber": "060-654321"
		  },
		  "premises": {
		    "name": "Restaurang Ankaret",
		    "address": { "street": "Storgatan 1", "postalCode": "85230", "city": "Sundsvall" },
		    "phoneNumber": "060-123456",
		    "restaurantNumber": "22810012",
		    "municipalityId": "2281"
		  },
		  "servingTo": ["PUBLIC", "CLOSED_COMPANY"],
		  "servingPeriods": [
		    { "audience": "PUBLIC", "period": "ALL_YEAR" },
		    { "audience": "CLOSED_COMPANY", "location": "OUTDOOR", "period": "ANNUAL_PERIOD", "from": "05-01", "to": "09-30" },
		    { "location": "OUTDOOR", "period": "ANNUAL_PERIOD", "from": "12-01", "to": "02-28" },
		    { "period": "FIXED_PERIOD", "fromDate": "2027-07-01", "toDate": "2027-07-04" }
		  ],
		  "servingHours": [
		    { "audience": "PUBLIC", "location": "INDOOR", "from": "11:00", "to": "01:00" },
		    { "location": "OUTDOOR", "from": "11:00", "to": "24:00" },
		    { "audience": "CLOSED_COMPANY", "from": "11:00", "to": "02:00", "description": "Fredag och lördag" },
		    { "from": "10:00", "to": "23:00" },
		    { "description": "Enligt överenskommelse" }
		  ],
		  "tasting": false,
		  "beverages": ["SPIRITS", "WINE", "STRONG_BEER", "OTHER_FERMENTED_BEVERAGES"],
		  "servingArea": { "description": "Matsal och bar", "drawingReference": "ritning.pdf", "seatsIndoor": 80, "seatsOutdoor": 40, "maxPersons": 150 },
		  "conditions": ["Ordningsvakt fredag och lördag efter klockan 22.", "Bordsservering på uteserveringen."],
		  "decision": {
		    "caseRegistrationNumber": "ALKT-2026-000300",
		    "outcome": "APPROVAL_WITH_CONDITIONS",
		    "decisionText": "Bifall med villkor",
		    "decisionMaker": "Alkoholhandläggare",
		    "decisionDate": "2026-10-29",
		    "executionDate": "2026-10-30",
		    "validFrom": "2026-11-01",
		    "replacesDecision": "2019-05-01 / IAN-2019-45",
		    "issuedBy": { "name": "Kim Karlsson", "title": "Handläggare", "unit": "Alkohol- och tobaksenheten" }
		  }
		}
		""";

	private static final String TOBACCO_PERMIT = """
		{
		  "caseRegistrationNumber": "ALKT-2026-000200",
		  "externalCaseRegistrationNumber": "KS 2026/123",
		  "permitHolder": {
		    "name": "Tobaksbolaget AB",
		    "organizationNumber": "5566778899",
		    "address": { "careOf": "Kim Karlsson", "street": "Storgatan 10", "postalCode": "852 30", "city": "Sundsvall" }
		  },
		  "pointOfSale": {
		    "name": "Tobaksaffären",
		    "address": { "street": "Köpmangatan 5", "postalCode": "86132", "city": "Timrå" },
		    "phoneNumber": "060-222222",
		    "propertyDesignation": "Timrå Vivsta 1:2",
		    "municipalityId": "2262"
		  },
		  "salesTypes": ["RETAIL", "DISTANCE_SELLING"],
		  "salesPeriod": { "period": "TEMPORARY", "from": "2026-11-01", "to": "2027-10-31" },
		  "information": "Försäljning får bara ske från disken.\\nTillståndet gäller inte e-cigaretter.",
		  "decision": {
		    "caseRegistrationNumber": "ALKT-2026-000200",
		    "decisionText": "Bifall",
		    "decisionMaker": "Tobakshandläggare",
		    "decisionDate": "2026-10-29",
		    "executionDate": "2026-10-30",
		    "validFrom": "2026-11-01",
		    "issuedBy": { "name": "Kim Karlsson", "title": "Handläggare", "unit": "Alkohol- och tobaksenheten" }
		  }
		}
		""";

	@Test
	void theServingPermitFillsThePlaceholdersOfItsCertificate() {
		final var result = toCertificateParameters(ALCOHOL, json(SERVING_PERMIT), new Decision().id("decision-id").outcome("APPROVAL_WITH_CONDITIONS"), "ALKT-2026-000300");

		assertThat(result).containsOnly(
			entry("caseNumber", "ALKT-2026-000300"),
			entry("decisionDate", "2026-10-29"),
			entry("decisionMaker", "Alkoholhandläggare"),
			entry("decisionText", "Bifall med villkor"),
			entry("replacesDecision", "2019-05-01 / IAN-2019-45"),
			entry("executionDate", "2026-10-30"),
			entry("issuedByNameAndTitle", "Kim Karlsson, Handläggare"),
			entry("issuedByUnit", "Alkohol- och tobaksenheten"),
			entry("decisionDateAndCaseNumber", "2026-10-29 / ALKT-2026-000300"),
			entry("validFrom", "2026-11-01"),
			entry("permitHolderName", "Bolaget AB"),
			entry("permitHolderOrgNumber", "556677-8899"),
			entry("permitHolderStreet", "Box 1"),
			entry("permitHolderPostalAddress", "852 30 Sundsvall"),
			entry("permitHolderPhone", "060-654321"),
			entry("premisesName", "Restaurang Ankaret"),
			entry("premisesStreet", "Storgatan 1"),
			entry("premisesPostalAddress", "85230 Sundsvall"),
			entry("premisesPhone", "060-123456"),
			entry("premisesRestaurantNumber", "22810012"),
			entry("servingAreaDescription", "Matsal och bar"),
			entry("servingAreaDrawingReference", "ritning.pdf"),
			entry("servingAreaMaxPersons", "150"),
			entry("servingAreaSeats", "80"),
			entry("scopeAllYear", "true"),
			entry("scopeAnnualPeriod", "true"),
			entry("scopePeriodFromTo", "05-01 – 09-30 (Slutet sällskap, uteservering), 12-01 – 02-28 (uteservering), 2027-07-01 – 2027-07-04"),
			entry("scopeTasting", "false"),
			entry("beverageBeer", "true"),
			entry("beverageWine", "true"),
			entry("beverageOtherFermented", "true"),
			entry("beverageSpirits", "true"),
			entry("servingToPublic", "true"),
			entry("servingToClosedCompany", "true"),
			entry("servingHours", """
				Allmänheten, inomhus: 11:00–01:00
				Uteservering: 11:00–24:00
				Slutet sällskap: 11:00–02:00 (Fredag och lördag)
				10:00–23:00
				Enligt överenskommelse"""),
			entry("conditions", "Ordningsvakt fredag och lördag efter klockan 22.\nBordsservering på uteserveringen."));
	}

	@Test
	void aServingPermitWithoutACaseNumberSendsItEmpty() {
		final var result = toCertificateParameters(ALCOHOL, json(SERVING_PERMIT), APPROVAL, " ");

		assertThat(result)
			.containsEntry("caseNumber", "")
			.containsEntry("decisionDateAndCaseNumber", "2026-10-29");
	}

	@Test
	void anAnnualPeriodWithoutDatesIsItsDescription() {
		final var result = toCertificateParameters(ALCOHOL, json("""
			{ "servingPeriods": [{ "period": "ANNUAL_PERIOD", "description": "Under skolloven" }] }
			"""), APPROVAL, "ALKT-2026-000123");

		assertThat(result)
			.containsEntry("scopeAnnualPeriod", "true")
			.containsEntry("scopePeriodFromTo", "Under skolloven");
	}

	/** The template is strict, so every placeholder it reads is sent, empty or unticked when the permit has no value. */
	@Test
	void aServingPermitWithoutValuesSendsEveryPlaceholderOfTheTemplateEmpty() {
		final var result = toCertificateParameters(ALCOHOL, json("{}"), APPROVAL, null);

		assertThat(result).containsOnlyKeys(SERVING_TEMPLATE_PLACEHOLDERS);
		assertThat(result.values()).allMatch(value -> "".equals(value) || "false".equals(value));
	}

	@Test
	void aTastingPermitTicksTheBoxOfTasting() {
		assertThat(toCertificateParameters(ALCOHOL, json("""
			{ "tasting": true }
			"""), APPROVAL, "ALKT-2026-000123")).containsEntry("scopeTasting", "true");
		assertThat(toCertificateParameters(ALCOHOL, json("""
			{ "servingPermitType": "TASTING" }
			"""), APPROVAL, "ALKT-2026-000123")).containsEntry("scopeTasting", "true");
	}

	@Test
	void aBlankValueCountsAsMissing() {
		final var result = toCertificateParameters(ALCOHOL, json("""
			{ "premises": { "name": " ", "address": { "postalCode": "85230" } }, "decision": { "issuedBy": { "title": "Handläggare" } } }
			"""), APPROVAL, "ALKT-2026-000123");

		assertThat(result)
			.containsEntry("premisesName", "")
			.containsEntry("premisesPostalAddress", "85230")
			.containsEntry("issuedByNameAndTitle", "Handläggare");
	}

	@Test
	void theTobaccoPermitFillsThePlaceholdersOfItsCertificate() {
		final var result = toCertificateParameters(TOBACCO, json(TOBACCO_PERMIT), APPROVAL, "ALKT-2026-000200");

		assertThat(result).containsOnly(
			entry("caseNumber", "ALKT-2026-000200"),
			entry("decisionDate", "2026-10-29"),
			entry("decisionMaker", "Tobakshandläggare"),
			entry("decisionText", "Bifall"),
			entry("executionDate", "2026-10-30"),
			entry("replacesDecision", ""),
			entry("issuedByNameAndTitle", "Kim Karlsson, Handläggare"),
			entry("issuedByUnit", "Alkohol- och tobaksenheten"),
			entry("decisionOfficerAndCaseNumber", "Kim Karlsson, ALKT-2026-000200"),
			entry("permitHolderName", "Tobaksbolaget AB"),
			entry("permitHolderOrgNumber", "5566778899"),
			entry("permitHolderStreet", "Storgatan 10"),
			entry("permitHolderPostalAddress", "852 30 Sundsvall"),
			entry("permitHolderCareOf", "Kim Karlsson"),
			entry("validityIndefinite", "false"),
			entry("validityTemporary", "true"),
			entry("validFrom", "2026-11-01"),
			entry("temporaryPeriod", "2026-11-01 – 2027-10-31"),
			entry("premisesName", "Tobaksaffären"),
			entry("premisesStreet", "Köpmangatan 5"),
			entry("premisesPostalAddress", "86132 Timrå"),
			entry("premisesPhone", "060-222222"),
			entry("premisesPropertyDesignation", "Timrå Vivsta 1:2"),
			entry("salesRetail", "true"),
			entry("salesWholesale", "false"),
			entry("salesOnline", "true"),
			entry("information", "Försäljning får bara ske från disken.\nTillståndet gäller inte e-cigaretter."));
	}

	/** The decision says from when the permit is valid, whenever the applicant wished to start selling. */
	@Test
	void aTobaccoPermitIsValidFromTheFirstDayOfItsDecision() {
		final var result = toCertificateParameters(TOBACCO, json("""
			{ "salesPeriod": { "period": "INDEFINITE", "from": "2026-12-01" }, "decision": { "validFrom": "2026-11-01" } }
			"""), APPROVAL, "ALKT-2026-000123");

		assertThat(result)
			.containsEntry("validityIndefinite", "true")
			.containsEntry("validityTemporary", "false")
			.containsEntry("validFrom", "2026-11-01")
			.containsEntry("temporaryPeriod", "");
		assertThat(toCertificateParameters(TOBACCO, json("""
			{ "salesPeriod": { "period": "INDEFINITE", "from": "2026-12-01" } }
			"""), APPROVAL, "ALKT-2026-000123")).containsEntry("validFrom", "");
	}

	/** The certificate names the case worker who issued the decision, never a case officer. */
	@Test
	void aTobaccoPermitNamesTheCaseWorkerWhoIssuedTheDecision() {
		assertThat(toCertificateParameters(TOBACCO, json("""
			{ "decision": { "caseOfficer": "Alex Andersson", "issuedBy": { "name": "Kim Karlsson" } } }
			"""), APPROVAL, "ALKT-2026-000123")).containsEntry("decisionOfficerAndCaseNumber", "Kim Karlsson, ALKT-2026-000123");
		assertThat(toCertificateParameters(TOBACCO, json("""
			{ "decision": { "caseOfficer": "Alex Andersson" } }
			"""), APPROVAL, "ALKT-2026-000123")).containsEntry("decisionOfficerAndCaseNumber", "ALKT-2026-000123");
	}

	/** The certificate shows the case number it is given, not one of the JSON parameter. */
	@Test
	void aTobaccoPermitShowsTheCaseNumberOfTheDecidingErrand() {
		final var result = toCertificateParameters(TOBACCO, json(TOBACCO_PERMIT), APPROVAL, "TSN 2026/0153");

		assertThat(result)
			.containsEntry("caseNumber", "TSN 2026/0153")
			.containsEntry("decisionOfficerAndCaseNumber", "Kim Karlsson, TSN 2026/0153");
	}

	/** The template is strict, so every placeholder it reads is sent, empty or unticked when the permit has no value. */
	@Test
	void aTobaccoPermitWithoutValuesSendsEveryPlaceholderOfTheTemplateEmpty() {
		final var result = toCertificateParameters(TOBACCO, json("{}"), APPROVAL, null);

		assertThat(result).containsOnlyKeys(TOBACCO_TEMPLATE_PLACEHOLDERS);
		assertThat(result.values()).allMatch(value -> "".equals(value) || "false".equals(value));
	}

	@Test
	void anApprovalWithConditionsWithoutConditionsGetsNoCertificate() {
		final var decision = new Decision().id("decision-id").outcome("APPROVAL_WITH_CONDITIONS");

		assertThatThrownBy(() -> toCertificateParameters(TOBACCO, json("""
			{ "conditions": [" "] }
			"""), decision, "ALKT-2026-000123"))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Decision decision-id is an approval with conditions but has no conditions, so no certificate is made");
		assertThat(toCertificateParameters(TOBACCO, json("""
			{ "conditions": ["Försäljning får bara ske från disken."] }
			"""), decision, "ALKT-2026-000123")).isInstanceOf(Map.class);
	}

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}
}
