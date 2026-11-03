package se.sundsvall.alkt.integration.templating.mapper;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.InvestigationSection;
import generated.se.sundsvall.supportmanagement.JsonParameter;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import generated.se.sundsvall.supportmanagement.Statement;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_COMPLETE_AT;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_NAME;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_AREA;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_CODE;
import static se.sundsvall.alkt.Constants.ERRAND_PARAMETER_PREMISES_STREET_ADDRESS;
import static se.sundsvall.alkt.Constants.MUNICIPALITY_ID_SUNDSVALL;
import static se.sundsvall.alkt.Constants.MUNICIPALITY_ID_TIMRA;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.findFormParameter;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toCaseNumber;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toParameterValues;
import static se.sundsvall.alkt.integration.supportmanagement.mapper.SupportManagementMapper.toPremisesMunicipalityId;

public final class DecisionBasisMapper {

	private static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");
	private static final String ROLE_PRIMARY = "PRIMARY";
	private static final String YES = "YES";
	private static final String RESCUE_SERVICE = "Medelpads Räddningstjänstförbund";
	private static final String ISSUED_BY_UNIT = "Alkohol- och tobaksenheten";
	private static final Map<String, String> ISSUING_AUTHORITIES = Map.of(
		MUNICIPALITY_ID_SUNDSVALL, "Individ- och arbetsmarknadsförvaltningen, Sundsvalls kommun",
		MUNICIPALITY_ID_TIMRA, "Miljö- och byggkontoret, Timrå kommun");
	private static final String HANDLER_TITLE = "handläggare";
	private static final String KNOWLEDGE_TEST_APPROVED = "APPROVED";
	private static final String KNOWLEDGE_BASIS_APPROVED = "har godkänt kunskapsprov";
	private static final List<String> FLOOR_PLAN_PURPOSES = List.of("FLOOR_PLAN", "KITCHEN_FLOOR_PLAN");
	private static final List<String> PREMISES_PREFIXES = List.of("premises", "pointOfSale");
	private static final Pattern CLOCK = Pattern.compile("^(\\d{2}:\\d{2}).*");
	private static final Pattern KNOWLEDGE_TEST = Pattern.compile("^PBI_KNOWLEDGE_TEST_(\\d+)_(TYPE|STATUS|DATE|COMMENT)$");

	private static final Map<String, String> RIGHTS_OF_DISPOSAL = Map.of(
		"PROVEN", "Ja",
		"PENDING", "Under utredning",
		"NOT_PROVEN", "Nej");

	private static final Map<String, String> KNOWLEDGE_TEST_STATUSES = Map.of(
		"APPROVED", "Godkänt",
		"FAILED", "Underkänt",
		"BOOKED", "Bokat",
		"RETAKE", "Omprov",
		"NOT_APPLICABLE", "Ej aktuellt");

	private static final Map<String, String> KNOWLEDGE_TEST_TYPES = Map.of(
		"PERMANENT_PUBLIC", "Stadigvarande serveringstillstånd till allmänheten",
		"PERMANENT_CLOSED_CATERING", "Stadigvarande serveringstillstånd i slutet sällskap vid cateringverksamhet",
		"PERMANENT_CLOSED", "Stadigvarande serveringstillstånd i slutet sällskap utan cateringverksamhet",
		"TEMPORARY_PUBLIC", "Tillfälligt serveringstillstånd till allmänheten",
		"TEMPORARY_CLOSED", "Tillfälligt serveringstillstånd i slutet sällskap",
		"TASTING", "Tillstånd för provsmakning",
		"FARM_SALES", "Gårdsförsäljning");

	private static final Map<String, String> FINANCING = Map.of(
		"ownFunds", "Egna medel",
		"bankLoans", "Banklån",
		"privateLoans", "Privatlån",
		"otherFinancing", "Annan finansiering");

	private static final Map<String, String> SERVING_HOURS = Map.of(
		"publicServingHoursIndoor", "Inomhus, allmänheten",
		"publicServingHoursOutdoor", "Utomhus, allmänheten",
		"closedCompanyServingHoursIndoor", "Inomhus, slutet sällskap",
		"closedCompanyServingHoursOutdoor", "Utomhus, slutet sällskap",
		"dailyServingHours", "",
		"salesHours", "");

	private static final Map<String, String> SERVING_PERIODS = Map.of(
		"publicServingPeriod", "publicServingPeriodDates",
		"closedCompanyServingPeriod", "closedCompanyServingPeriodDates",
		"salesPeriod", "salesPeriodDates");

	public record Labels(JsonNode formSchema, Map<String, String> decisionOutcomes, Map<String, String> statementOutcomes) {}

	private DecisionBasisMapper() {}

	public static Map<String, Object> toTemplateParameters(final Errand errand, final Investigation investigation, final List<Statement> statements,
		final List<ErrandAttachment> attachments, final Map<String, String> legalIds, final Labels labels, final LocalDate today) {
		final var form = findFormParameter(errand).map(JsonParameter::getValue).orElse(MissingNode.getInstance());
		final var schema = labels.formSchema();
		final var sections = Optional.ofNullable(investigation.getSections()).orElse(emptyList());
		final var premisesExamination = sections.stream()
			.filter(section -> "premises".equals(section.getSectionKey()))
			.findFirst()
			.map(section -> toParameterValues(section.getParameters()))
			.orElse(Map.of());
		final var stakeholders = Optional.ofNullable(errand.getStakeholders()).orElse(emptyList());
		final var applicant = stakeholders.stream().filter(stakeholder -> ROLE_PRIMARY.equals(stakeholder.getRole())).findFirst();
		final var personsOfInfluence = stakeholders.stream().filter(DecisionBasisMapper::isPersonOfInfluence).toList();
		final var errandValues = toParameterValues(errand.getParameters());
		final var address = toPremisesAddress(form, applicant, errandValues);
		final var referrals = Optional.ofNullable(statements).orElse(emptyList());

		final var parameters = new LinkedHashMap<String, Object>();
		parameters.put("documentDate", today.toString());
		parameters.put("caseNumber", Objects.toString(toCaseNumber(errand), ""));
		parameters.put("caseType", toCaseType(errand));
		// TODO: the case worker's name, looked up by investigatorUserId in the employee API.
		parameters.put("handlerName", "");
		parameters.put("handlerTitle", HANDLER_TITLE);
		parameters.put("issuedByUnit", ISSUED_BY_UNIT);
		parameters.put("issuedByAuthority", ISSUING_AUTHORITIES.get(toPremisesMunicipalityId(errand)));
		parameters.put("communication", toPlainTextLines(toSectionText(sections, "communication")));
		parameters.put("childPerspectiveHtml", toSectionText(sections, "child_perspective"));
		parameters.put("marketingHtml", toSectionText(sections, "marketing"));
		parameters.put("selfMonitoringHtml", toSectionText(sections, "self_monitoring"));
		parameters.put("legislationHtml", toSectionText(sections, "legal_basis"));
		parameters.put("exciseTaxHtml", "");
		parameters.put("proposal", Map.of("points", toProposalPoints(investigation, labels)));
		parameters.put("applicant", Map.of(
			"name", applicant.map(DecisionBasisMapper::toName).orElse(""),
			"orgNumber", applicant.map(stakeholder -> toParameterValues(stakeholder.getParameters()).getOrDefault("organizationNumber", "")).orElse(""),
			"street", applicant.map(Stakeholder::getAddress).orElse(""),
			"postalAddress", applicant.map(stakeholder -> toPostalAddress(stakeholder.getZipCode(), stakeholder.getCity())).orElse("")));
		parameters.put("company", Map.of("ownershipHtml", toSectionText(sections, "ownership")));
		parameters.put("application", toApplication(errand, form, schema, sections, attachments, personsOfInfluence));
		parameters.put("premises", Map.ofEntries(
			Map.entry("name", findText(findPremisesValue(form, "Name"))
				.or(() -> Optional.ofNullable(errandValues.get(ERRAND_PARAMETER_PREMISES_NAME)))
				.or(() -> applicant.map(Stakeholder::getOrganizationName))
				.orElse("")),
			Map.entry("street", address.street()),
			Map.entry("postalAddress", address.postalAddress()),
			Map.entry("seatsIndoor", findText(form.path("seating").path("seatsIndoor")).orElse("")),
			Map.entry("seatsOutdoor", findText(form.path("seating").path("seatsOutdoor")).orElse("")),
			Map.entry("maxPersons", findText(form.path("maxPersonsInPremises").path("maxPersons")).orElse("")),
			Map.entry("inspectedAt", premisesExamination.getOrDefault("PREMISES_INSPECTED_AT", "")),
			Map.entry("rightOfDisposalLabel", toLabel(RIGHTS_OF_DISPOSAL, premisesExamination.get("PREMISES_RIGHT_OF_DISPOSAL"))),
			Map.entry("rightOfDisposalNote", premisesExamination.getOrDefault("PREMISES_RIGHT_OF_DISPOSAL_NOTE", "")),
			Map.entry("servingAreaDescription", premisesExamination.getOrDefault("PREMISES_SERVING_AREA", "")),
			Map.entry("salesAreaDescription", toHtml(form.path("salesAreaDescription"))),
			Map.entry("leaseHtml", toSectionText(sections, "lease")),
			Map.entry("floorPlanFiles", toFileNames(attachments, FLOOR_PLAN_PURPOSES)),
			Map.entry("fireSafety", toFireSafety(referrals, labels)),
			Map.entry("fireSafetyHtml", toSectionText(sections, "fire_safety"))));
		parameters.put("personsOfInfluence", personsOfInfluence.stream().map(person -> toPersonOfInfluence(person, legalIds)).toList());
		parameters.put("statements", referrals.stream().map(statement -> toStatement(statement, labels)).toList());
		parameters.put("investigation", Map.of(
			"recommendationLabel", toLabel(labels.decisionOutcomes(), investigation.getRecommendation()),
			"financialAssessmentHtml", toSectionText(sections, "financial_suitability"),
			"premisesRemarksHtml", Optional.of(toSectionText(sections, "premises")).filter(StringUtils::isNotEmpty).orElseGet(() -> toSectionText(sections, "sales_point")),
			"conclusionHtml", toSanitizedHtml(investigation.getConclusion()),
			"recommendationMotivationHtml", toSanitizedHtml(investigation.getRecommendationMotivation())));
		return parameters;
	}

	private static Map<String, Object> toApplication(final Errand errand, final JsonNode form, final JsonNode schema, final List<InvestigationSection> sections,
		final List<ErrandAttachment> attachments, final List<Stakeholder> personsOfInfluence) {
		final var application = new LinkedHashMap<String, Object>();
		application.put("receivedAt", toDate(errand.getCreated()));
		application.put("completeAt", toParameterValues(errand.getParameters()).getOrDefault(ERRAND_PARAMETER_COMPLETE_AT, ""));
		application.put("servingTo", toChoice(form, schema, "servingAudience"));
		application.put("beverages", toChoices(form, schema, "beverages"));
		application.put("period", toPeriod(form, schema));
		application.put("servingHours", SERVING_HOURS.entrySet().stream()
			.map(hours -> toTimeRange(form.path(hours.getKey())).map(range -> Stream.of(hours.getValue(), range).filter(StringUtils::isNotEmpty).collect(joining(" "))))
			.flatMap(Optional::stream)
			.toList());
		application.put("orientation", toChoices(form, schema, "businessOrientationTypes"));
		application.put("orientationHtml", toHtml(form.path("businessOrientationDescription")));
		application.put("businessDescriptionHtml", toHtml(form.path("businessDescription")));
		application.put("orderAndSobrietyHtml", toHtml(form.path("orderAndSobriety")));
		application.put("rightOfDisposal", toChoices(form, schema, "premisesRightOfUseDocuments"));
		application.put("distanceSelling", toChoice(form, schema, "includesDistanceSelling"));
		application.put("distanceSellingWebsite", findText(form.path("distanceSellingWebsite")).orElse(""));
		application.put("particularlyDangerousProducts", toChoice(form, schema, "includesParticularlyDangerousProducts"));
		application.put("supplierAgreement", toChoice(form, schema, "hasSupplierAgreement"));
		application.put("knowledgeBasis", personsOfInfluence.stream()
			.flatMap(person -> toKnowledgeTests(person).values().stream())
			.anyMatch(test -> KNOWLEDGE_TEST_APPROVED.equals(test.get("STATUS"))) ? KNOWLEDGE_BASIS_APPROVED : "");
		application.put("responsibleStaff", form.path("responsibleServingStaff").values().stream()
			.filter(JsonNode::isObject)
			.map(staff -> Map.of(
				"name", Stream.of(findText(staff.path("firstName")), findText(staff.path("lastName"))).flatMap(Optional::stream).collect(joining(" ")),
				"dateOfBirth", findText(staff.path("dateOfBirth")).orElse(""),
				"fromDate", findText(staff.path("fromDate")).orElse("")))
			.toList());
		application.put("staffingHtml", toSectionText(sections, "staffing"));
		application.put("servesFood", toChoice(form, schema, "servesFood"));
		application.put("menuFiles", toFileNames(attachments, List.of("MENU")));
		application.put("drinksMenuFiles", toFileNames(attachments, List.of("DRINKS_MENU")));
		application.put("foodAndBeveragesHtml", toSectionText(sections, "food_and_beverages"));
		application.put("financing", Map.of(
			"sources", FINANCING.entrySet().stream()
				.flatMap(source -> form.path(source.getKey()).values().stream().map(entry -> Map.of(
					"kind", findText(entry.path("loanType")).orElse(source.getValue()),
					"amount", toAmount(entry.path("amountSek")),
					"lender", findText(entry.path("bank")).or(() -> findText(entry.path("lender"))).orElse(""))))
				.toList(),
			"notesHtml", toHtml(form.path("financingNotes"))));
		return application;
	}

	private static JsonNode findPremisesValue(final JsonNode form, final String field) {
		return PREMISES_PREFIXES.stream()
			.map(prefix -> form.path(prefix + field))
			.filter(value -> !value.isMissingNode())
			.findFirst()
			.orElse(MissingNode.getInstance());
	}

	private record Address(String street, String postalAddress) {}

	private static Address toPremisesAddress(final JsonNode form, final Optional<Stakeholder> applicant, final Map<String, String> errandValues) {
		if (YES.equals(findPremisesValue(form, "AddressSameAsCompany").asString(""))) {
			return applicant
				.map(owner -> new Address(Objects.toString(owner.getAddress(), ""), toPostalAddress(owner.getZipCode(), owner.getCity())))
				.orElse(new Address("", ""));
		}
		final var address = findPremisesValue(form, "Address");
		final var street = findText(address.path("street"));
		final var postalCode = findText(address.path("postalCode"));
		final var city = findText(address.path("city"));
		if (street.isPresent() || postalCode.isPresent() || city.isPresent()) {
			return new Address(street.orElse(""), toPostalAddress(postalCode.orElse(null), city.orElse(null)));
		}
		return new Address(errandValues.getOrDefault(ERRAND_PARAMETER_PREMISES_STREET_ADDRESS, ""),
			toPostalAddress(errandValues.get(ERRAND_PARAMETER_PREMISES_POSTAL_CODE), errandValues.get(ERRAND_PARAMETER_PREMISES_POSTAL_AREA)));
	}

	private static String toPostalAddress(final String postalCode, final String city) {
		return Stream.of(postalCode, city).filter(StringUtils::isNotBlank).collect(joining(" "));
	}

	private static String toCaseType(final Errand errand) {
		final var labels = Optional.ofNullable(errand.getLabels()).orElse(emptyList());
		return Stream.of("SUBTYPE", "TYPE")
			.flatMap(classification -> labels.stream().filter(label -> classification.equals(label.getClassification())))
			.map(ErrandLabel::getDisplayName)
			.filter(StringUtils::isNotBlank)
			.findFirst()
			.map(name -> name.toLowerCase(Locale.forLanguageTag("sv-SE")))
			.orElse("");
	}

	private static String toSectionText(final List<InvestigationSection> sections, final String sectionKey) {
		return sections.stream()
			.filter(section -> sectionKey.equals(section.getSectionKey()))
			.findFirst()
			.map(InvestigationSection::getText)
			.filter(StringUtils::isNotBlank)
			.map(HtmlSanitizer::sanitize)
			.orElse("");
	}

	private static List<String> toProposalPoints(final Investigation investigation, final Labels labels) {
		final var points = toPlainTextLines(investigation.getRecommendationMotivation()).lines().filter(StringUtils::isNotBlank).toList();
		if (!points.isEmpty()) {
			return points;
		}
		return Optional.of(toLabel(labels.decisionOutcomes(), investigation.getRecommendation())).filter(StringUtils::isNotEmpty).map(List::of).orElse(emptyList());
	}

	private static String toPeriod(final JsonNode form, final JsonNode schema) {
		return Stream.concat(
			SERVING_PERIODS.entrySet().stream().map(period -> Stream.of(Optional.of(toChoice(form, schema, period.getKey())).filter(StringUtils::isNotEmpty), toDateRange(form.path(period.getValue()))).flatMap(Optional::stream).collect(joining(", "))),
			toDateRange(form.path("permitPeriodDates")).stream())
			.filter(StringUtils::isNotEmpty)
			.collect(joining(". "));
	}

	private static Optional<String> toDateRange(final JsonNode range) {
		final var from = findText(range.path("fromDate"));
		final var to = findText(range.path("toDate"));
		return from.isEmpty() && to.isEmpty() ? Optional.empty() : Optional.of("%s – %s".formatted(from.orElse(""), to.orElse("")));
	}

	private static Optional<String> toTimeRange(final JsonNode range) {
		final var from = toClock(range.path("from"));
		final var to = toClock(range.path("to"));
		return from.isEmpty() && to.isEmpty() ? Optional.empty() : Optional.of("%s–%s".formatted(from.orElse(""), to.orElse("")));
	}

	private static Optional<String> toClock(final JsonNode time) {
		return findText(time).map(CLOCK::matcher).filter(Matcher::matches).map(matcher -> matcher.group(1));
	}

	private static String toAmount(final JsonNode amount) {
		final var given = findText(amount).orElse("");
		final var digits = StringUtils.deleteWhitespace(given);
		return StringUtils.isNumeric(digits) && !digits.isEmpty() ? NumberFormat.getIntegerInstance(Locale.forLanguageTag("sv-SE")).format(new BigInteger(digits)) + " kr" : given;
	}

	private static String toChoice(final JsonNode form, final JsonNode schema, final String field) {
		return findText(form.path(field)).map(code -> findTitle(schema.path("properties").path(field).path("oneOf"), code)).orElse("");
	}

	private static String toChoices(final JsonNode form, final JsonNode schema, final String field) {
		final var options = schema.path("properties").path(field).path("items").path("oneOf");
		return form.path(field).values().stream().map(DecisionBasisMapper::findText).flatMap(Optional::stream).map(code -> findTitle(options, code)).collect(joining(", "));
	}

	private static String findTitle(final JsonNode options, final String code) {
		return options.values().stream()
			.filter(option -> code.equals(option.path("const").asString(null)))
			.map(option -> option.path("title").asString(null))
			.filter(StringUtils::isNotBlank)
			.findFirst()
			.orElse(code);
	}

	private static String toLabel(final Map<String, String> labels, final String code) {
		return Optional.ofNullable(code).filter(StringUtils::isNotBlank).map(name -> labels.getOrDefault(name, name)).orElse("");
	}

	private static String toHtml(final JsonNode value) {
		return findText(value).map(HtmlSanitizer::sanitize).orElse("");
	}

	private static Optional<String> findText(final JsonNode value) {
		return Optional.of(value)
			.filter(node -> node.isString() || node.isNumber())
			.map(node -> node.asString().strip())
			.filter(StringUtils::isNotEmpty);
	}

	private static List<String> toFileNames(final List<ErrandAttachment> attachments, final List<String> purposes) {
		return Optional.ofNullable(attachments).orElse(emptyList()).stream()
			.filter(attachment -> Optional.ofNullable(attachment.getPurpose()).map(ErrandAttachmentPurpose::getName).filter(purposes::contains).isPresent())
			.map(ErrandAttachment::getFileName)
			.filter(StringUtils::isNotBlank)
			.toList();
	}

	public static boolean isPersonOfInfluence(final Stakeholder stakeholder) {
		return "true".equals(toParameterValues(stakeholder.getParameters()).get("PBI"));
	}

	private static Map<String, Object> toPersonOfInfluence(final Stakeholder person, final Map<String, String> legalIds) {
		final var values = toParameterValues(person.getParameters());
		final var tests = toKnowledgeTests(person).values();
		final var shownTest = tests.stream()
			.filter(test -> KNOWLEDGE_TEST_APPROVED.equals(test.get("STATUS")))
			.reduce((earlier, later) -> later)
			.or(() -> tests.stream().reduce((earlier, later) -> later))
			.orElse(Map.of());
		return Map.of(
			"name", toName(person),
			"personalNumber", Optional.ofNullable(person.getExternalId())
				.map(legalIds::get)
				.map(DecisionBasisMapper::toHyphenatedIdentity)
				.orElse(""),
			"roles", values.getOrDefault("PBI_ROLE", ""),
			"motivation", toPlainTextLines(values.get("PBI_MOTIVATION")),
			"knowledgeTest", Map.of(
				"statusLabel", toLabel(KNOWLEDGE_TEST_STATUSES, shownTest.get("STATUS")),
				"testType", toLabel(KNOWLEDGE_TEST_TYPES, shownTest.get("TYPE")),
				"testedAt", shownTest.getOrDefault("DATE", ""),
				"comment", shownTest.getOrDefault("COMMENT", "")));
	}

	/**
	 * The knowledge tests of a person of influence, by test number. Each test is kept as stakeholder
	 * parameters with the number in the key, PBI_KNOWLEDGE_TEST_1_STATUS = APPROVED, and comes back as
	 * { 1 = { STATUS = APPROVED, ... } } in number order.
	 */
	private static Map<Integer, Map<String, String>> toKnowledgeTests(final Stakeholder person) {
		final var values = toParameterValues(person.getParameters());
		final var tests = new TreeMap<Integer, Map<String, String>>();
		values.forEach((key, value) -> Optional.of(KNOWLEDGE_TEST.matcher(key)).filter(Matcher::matches)
			.ifPresent(matcher -> tests.computeIfAbsent(Integer.valueOf(matcher.group(1)), number -> new LinkedHashMap<>()).put(matcher.group(2), value)));
		return tests;
	}

	private static String toName(final Stakeholder stakeholder) {
		return Optional.ofNullable(stakeholder.getOrganizationName()).filter(StringUtils::isNotBlank)
			.orElseGet(() -> Stream.of(stakeholder.getFirstName(), stakeholder.getLastName()).filter(StringUtils::isNotBlank).collect(joining(" ")));
	}

	private static String toHyphenatedIdentity(final String legalId) {
		return legalId.length() > 4 && StringUtils.isNumeric(legalId) ? legalId.substring(0, legalId.length() - 4) + "-" + legalId.substring(legalId.length() - 4) : legalId;
	}

	private static Map<String, Object> toStatement(final Statement statement, final Labels labels) {
		return Map.of(
			"counterparty", Objects.toString(statement.getCounterpartyName(), ""),
			"sentAt", toDate(statement.getSentAt()),
			"respondedAt", toDate(statement.getRespondedAt()),
			"status", Objects.toString(statement.getStatus(), ""),
			"outcomeLabel", toLabel(labels.statementOutcomes(), statement.getOutcome()),
			"responseText", toPlainTextLines(statement.getResponseText()));
	}

	private static String toFireSafety(final List<Statement> statements, final Labels labels) {
		return statements.stream()
			.filter(statement -> RESCUE_SERVICE.equals(statement.getCounterpartyName()) && isNotBlank(statement.getOutcome()))
			.max(Comparator.comparing(Statement::getRespondedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(statement -> statement.getCounterpartyName() + ": " + toLabel(labels.statementOutcomes(), statement.getOutcome()))
			.orElse("");
	}

	private static String toDate(final OffsetDateTime timestamp) {
		return Optional.ofNullable(timestamp).map(time -> time.atZoneSameInstant(SWEDISH_TIME).toLocalDate().toString()).orElse("");
	}

	private static String toSanitizedHtml(final String html) {
		return Optional.ofNullable(html).filter(StringUtils::isNotBlank).map(HtmlSanitizer::sanitize).orElse("");
	}

	private static String toPlainTextLines(final String html) {
		return Optional.ofNullable(html).map(HtmlSanitizer::toPlainTextLines).orElse("");
	}
}
