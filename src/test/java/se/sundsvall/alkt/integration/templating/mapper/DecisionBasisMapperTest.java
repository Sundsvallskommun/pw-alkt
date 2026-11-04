package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.InvestigationSection;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.Parameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import generated.se.sundsvall.supportmanagement.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.alkt.integration.templating.mapper.DecisionBasisMapper.Labels;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static se.sundsvall.alkt.integration.templating.mapper.DecisionBasisMapper.toTemplateParameters;

class DecisionBasisMapperTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-11-03");
	private static final Parameter IN_SUNDSVALL = new Parameter().key("premisesMunicipalityId").values(List.of("2281"));
	private static final Labels NO_LABELS = new Labels(MissingNode.getInstance(), Map.of(), Map.of());

	private static Parameter parameter(final String key, final String value) {
		return new Parameter().key(key).values(List.of(value));
	}

	@Test
	void mapsTheErrandItsApplicationInvestigationAndReferralsOfAServingPermit() {
		final var form = JsonMapper.builder().build().readTree("""
			{
			  "premisesName": "Restaurang Kajen",
			  "premisesAddressSameAsCompany": "NO",
			  "premisesAddress": { "street": "Hamngatan 4", "postalCode": "852 30", "city": "Sundsvall" },
			  "servingAudience": "PUBLIC",
			  "beverages": ["SPIRITS", "WINE"],
			  "publicServingPeriod": "ALL_YEAR",
			  "publicServingHoursIndoor": { "from": "11:00:00+02:00", "to": "01:00:00+02:00" },
			  "publicServingHoursOutdoor": { "from": "11:00:00+02:00", "to": "22:00:00+02:00" },
			  "seating": { "seatsIndoor": 80, "seatsOutdoor": 40 },
			  "maxPersonsInPremises": { "maxPersons": 150 },
			  "responsibleServingStaff": [ { "firstName": "Karin", "lastName": "Lund", "dateOfBirth": "1982-05-05", "fromDate": "2026-06-01" } ],
			  "ownFunds": [ { "amountSek": "100000" } ],
			  "bankLoans": [ { "bank": "Handelsbanken", "amountSek": "250000" } ],
			  "orderAndSobriety": "<p>Två ordningsvakter fredag och lördag</p>"
			}
			""");
		final var errand = new Errand().errandNumber("ALKT-2026-000123").created(OffsetDateTime.parse("2026-09-14T09:30:00+02:00"))
			.labels(List.of(new ErrandLabel().classification("TYPE").displayName("Stadigvarande serveringstillstånd")))
			.parameters(List.of(IN_SUNDSVALL, parameter("completeAt", "2026-10-01")))
			.jsonParameters(List.of(new JsonParameter().key("aot_alcohol_serving_permit_application_permanent_serving").value(form)))
			.stakeholders(List.of(
				new Stakeholder().role("PRIMARY").organizationName("Kajen Krog AB").address("Storgatan 1").zipCode("85230").city("Sundsvall")
					.parameters(List.of(parameter("organizationNumber", "559162-8136"))),
				new Stakeholder().role("CONTACT").externalId("party-of-karl").firstName("Karl").lastName("Berg")
					.parameters(List.of(parameter("PBI", "true"), parameter("PBI_ROLE", "Styrelseledamot"), parameter("PBI_MOTIVATION", "<p>Äger 60 %</p>"),
						parameter("PBI_KNOWLEDGE_TEST_1_STATUS", "FAILED"), parameter("PBI_KNOWLEDGE_TEST_1_DATE", "2026-06-11"),
						parameter("PBI_KNOWLEDGE_TEST_2_STATUS", "APPROVED"), parameter("PBI_KNOWLEDGE_TEST_2_DATE", "2026-08-20")))));
		final var investigation = new Investigation().recommendation("APPROVAL").recommendationMotivation("<p>Bifall</p><p>Villkor om ordningsvakter</p>")
			.conclusion("<p>Sökanden bedöms lämplig.</p>")
			.sections(List.of(
				new InvestigationSection().sectionKey("ownership").text("<p>Aktiebolag med aktiekapital 25 000 kr</p>"),
				new InvestigationSection().sectionKey("communication").text("<div>Kommunicerat 2026-10-20</div><div>Inga synpunkter</div>"),
				new InvestigationSection().sectionKey("premises").text("")
					.parameters(List.of(parameter("PREMISES_INSPECTED_AT", "2026-10-02"), parameter("PREMISES_RIGHT_OF_DISPOSAL", "PROVEN"))),
				new InvestigationSection().sectionKey("legal_basis").text("<h3>Alkohollagen</h3>")));
		final var statements = List.of(
			new Statement().counterpartyName("Medelpads Räddningstjänstförbund").status("RESPONDED").sentAt(OffsetDateTime.parse("2026-09-20T10:00:00+02:00"))
				.respondedAt(OffsetDateTime.parse("2026-10-05T23:30:00Z")).outcome("NO_OBJECTION").responseText("<p>Inget att erinra.</p>"),
			new Statement().counterpartyName("Polismyndigheten").status("DRAFT"));
		final var schema = JsonMapper.builder().build().readTree("""
			{
			  "properties": {
			    "servingAudience": { "oneOf": [ { "const": "PUBLIC", "title": "Allmänheten" } ] },
			    "beverages": { "items": { "oneOf": [ { "const": "SPIRITS", "title": "Spritdrycker" }, { "const": "WINE", "title": "Vin" } ] } },
			    "publicServingPeriod": { "oneOf": [ { "const": "ALL_YEAR", "title": "Året runt" } ] }
			  }
			}
			""");
		final var labels = new Labels(schema, Map.of("APPROVAL", "Bifall"), Map.of("NO_OBJECTION", "Inget att erinra"));
		final var attachments = List.of(
			new ErrandAttachment().fileName("Planritning.pdf").purpose(new ErrandAttachmentPurpose().name("FLOOR_PLAN")),
			new ErrandAttachment().fileName("Meny.pdf").purpose(new ErrandAttachmentPurpose().name("MENU")));

		final var parameters = toTemplateParameters(errand, investigation, statements, attachments, Map.of("party-of-karl", "198001011234"), labels, TODAY);

		assertThat(parameters)
			.containsEntry("documentDate", "2026-11-03")
			.containsEntry("caseNumber", "ALKT-2026-000123")
			.containsEntry("caseType", "stadigvarande serveringstillstånd")
			.containsEntry("issuedByAuthority", "Individ- och arbetsmarknadsförvaltningen, Sundsvalls kommun")
			.containsEntry("communication", "Kommunicerat 2026-10-20\nInga synpunkter")
			.containsEntry("legislationHtml", "<h3>Alkohollagen</h3>")
			.containsEntry("proposal", Map.of("points", List.of("Bifall", "Villkor om ordningsvakter")))
			.containsEntry("applicant", Map.of("name", "Kajen Krog AB", "orgNumber", "559162-8136", "street", "Storgatan 1", "postalAddress", "85230 Sundsvall"))
			.containsEntry("company", Map.of("ownershipHtml", "<p>Aktiebolag med aktiekapital 25 000 kr</p>"))
			.containsEntry("statements", List.of(
				Map.of("counterparty", "Medelpads Räddningstjänstförbund", "sentAt", "2026-09-20", "respondedAt", "2026-10-06", "status", "RESPONDED",
					"outcomeLabel", "Inget att erinra", "responseText", "Inget att erinra."),
				Map.of("counterparty", "Polismyndigheten", "sentAt", "", "respondedAt", "", "status", "DRAFT", "outcomeLabel", "", "responseText", "")))
			.containsEntry("personsOfInfluence", List.of(Map.of("name", "Karl Berg", "personalNumber", "19800101-1234", "roles", "Styrelseledamot",
				"motivation", "Äger 60 %", "knowledgeTest", Map.of("statusLabel", "Godkänt", "testType", "", "testedAt", "2026-08-20", "comment", ""))));
		assertThat(parameters.get("application")).asInstanceOf(MAP)
			.containsEntry("receivedAt", "2026-09-14")
			.containsEntry("completeAt", "2026-10-01")
			.containsEntry("servingTo", "Allmänheten")
			.containsEntry("beverages", "Spritdrycker, Vin")
			.containsEntry("period", "Året runt")
			.containsEntry("servingHours", List.of("Inomhus, allmänheten 11:00–01:00", "Utomhus, allmänheten 11:00–22:00"))
			.containsEntry("orderAndSobrietyHtml", "<p>Två ordningsvakter fredag och lördag</p>")
			.containsEntry("knowledgeBasis", "har godkänt kunskapsprov")
			.containsEntry("responsibleStaff", List.of(Map.of("name", "Karin Lund", "dateOfBirth", "1982-05-05", "fromDate", "2026-06-01")))
			.containsEntry("menuFiles", List.of("Meny.pdf"))
			.containsEntry("financing", Map.of("sources", List.of(Map.of("kind", "Egna medel", "amount", "100 000 kr", "lender", ""),
				Map.of("kind", "Banklån", "amount", "250 000 kr", "lender", "Handelsbanken")), "notesHtml", ""));
		assertThat(parameters.get("premises")).asInstanceOf(MAP)
			.containsEntry("name", "Restaurang Kajen")
			.containsEntry("street", "Hamngatan 4")
			.containsEntry("postalAddress", "852 30 Sundsvall")
			.containsEntry("seatsIndoor", "80")
			.containsEntry("maxPersons", "150")
			.containsEntry("inspectedAt", "2026-10-02")
			.containsEntry("rightOfDisposalLabel", "Ja")
			.containsEntry("floorPlanFiles", List.of("Planritning.pdf"))
			.containsEntry("fireSafety", "Medelpads Räddningstjänstförbund: Inget att erinra");
		assertThat(parameters.get("investigation")).asInstanceOf(MAP)
			.containsEntry("recommendationLabel", "Bifall")
			.containsEntry("conclusionHtml", "<p>Sökanden bedöms lämplig.</p>")
			.containsEntry("premisesRemarksHtml", "")
			.containsEntry("financialAssessmentHtml", "");
	}

	@Test
	void sendsEveryKeyOfTheTemplatesForAnErrandWithoutApplicationSectionsOrReferrals() {
		final var parameters = toTemplateParameters(new Errand().parameters(List.of(IN_SUNDSVALL)), new Investigation(), List.of(), List.of(), Map.of(), NO_LABELS, TODAY);

		assertThat(parameters).containsOnlyKeys("documentDate", "caseNumber", "caseType", "handlerName", "handlerTitle", "issuedByUnit", "issuedByAuthority",
			"communication", "childPerspectiveHtml", "marketingHtml", "selfMonitoringHtml", "legislationHtml", "exciseTaxHtml", "proposal", "applicant", "company",
			"application", "premises", "personsOfInfluence", "statements", "investigation");
		assertThat(parameters.get("application")).asInstanceOf(MAP).containsOnlyKeys("receivedAt", "completeAt",
			"servingTo", "beverages", "period", "servingHours", "orientation", "orientationHtml", "businessDescriptionHtml", "orderAndSobrietyHtml", "rightOfDisposal",
			"distanceSelling", "distanceSellingWebsite", "particularlyDangerousProducts", "supplierAgreement", "knowledgeBasis", "responsibleStaff", "staffingHtml",
			"servesFood", "menuFiles", "drinksMenuFiles", "foodAndBeveragesHtml", "financing");
		assertThat(parameters.get("premises")).asInstanceOf(MAP).containsOnlyKeys("name", "street", "postalAddress",
			"seatsIndoor", "seatsOutdoor", "maxPersons", "inspectedAt", "rightOfDisposalLabel", "rightOfDisposalNote", "servingAreaDescription", "salesAreaDescription",
			"leaseHtml", "floorPlanFiles", "fireSafety", "fireSafetyHtml");
		assertThat(parameters).containsEntry("proposal", Map.of("points", List.of())).containsEntry("statements", List.of()).containsEntry("communication", "");
	}

	@Test
	void readsTheAddressOfTheCompanyWhenThePointOfSaleIsThere() {
		final var form = JsonMapper.builder().build().readTree("""
			{ "pointOfSaleName": "Kiosken Torget", "pointOfSaleAddressSameAsCompany": "YES", "pointOfSaleAddress": { "street": "Gammal gata 9" } }
			""");
		final var errand = new Errand().parameters(List.of(IN_SUNDSVALL))
			.jsonParameters(List.of(new JsonParameter().key("aot_tobacco_sales_permit_application").value(form)))
			.stakeholders(List.of(new Stakeholder().role("PRIMARY").organizationName("Torgkiosken AB").address("Torget 2").zipCode("852 31").city("Sundsvall")));

		final var parameters = toTemplateParameters(errand, new Investigation(), List.of(), List.of(), Map.of(), NO_LABELS, TODAY);

		assertThat(parameters.get("premises")).asInstanceOf(MAP)
			.containsEntry("name", "Kiosken Torget")
			.containsEntry("street", "Torget 2")
			.containsEntry("postalAddress", "852 31 Sundsvall");
	}

	@Test
	void readsThePremisesFromTheErrandWhenItHasNoApplicationForm() {
		final var errand = new Errand().parameters(List.of(IN_SUNDSVALL, parameter("premisesName", "Runt Hörnet"), parameter("premisesStreetAddress", "Storgatan 33"),
			parameter("premisesPostalCode", "852 30"), parameter("premisesPostalArea", "Sundsvall")));

		final var parameters = toTemplateParameters(errand, new Investigation(), List.of(), List.of(), Map.of(), NO_LABELS, TODAY);

		assertThat(parameters.get("premises")).asInstanceOf(MAP)
			.containsEntry("name", "Runt Hörnet")
			.containsEntry("street", "Storgatan 33")
			.containsEntry("postalAddress", "852 30 Sundsvall");
	}

	@Test
	void findsTheRescueServiceWhateverTheCaseAndSpacesAroundItsName() {
		final var statement = new Statement().counterpartyName(" medelpads räddningstjänstförbund ").status("RESPONDED").outcome("NO_OBJECTION");

		final var parameters = toTemplateParameters(new Errand().parameters(List.of(IN_SUNDSVALL)), new Investigation(), List.of(statement), List.of(), Map.of(), NO_LABELS, TODAY);

		assertThat(parameters.get("premises")).asInstanceOf(MAP).containsEntry("fireSafety", "Medelpads Räddningstjänstförbund: NO_OBJECTION");
	}

	@Test
	void leavesOutStylesAndTagsThatSpanSeveralLinesOfAReferralAnswer() {
		final var answer = "<style>\np.MsoNormal {margin:0cm}\n</style><p>Polisen har inget\n<span\nclass=\"x\">att erinra</span></p>";

		final var parameters = toTemplateParameters(new Errand().parameters(List.of(IN_SUNDSVALL)), new Investigation(), List.of(new Statement().counterpartyName("Polismyndigheten").responseText(answer)),
			List.of(), Map.of(), NO_LABELS, TODAY);

		assertThat(parameters.get("statements")).asInstanceOf(LIST).singleElement().asInstanceOf(MAP)
			.containsEntry("responseText", "Polisen har inget att erinra");
	}
}
