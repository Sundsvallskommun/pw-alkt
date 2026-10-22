package se.sundsvall.alkt.integration.templating.mapper;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import se.sundsvall.alkt.integration.templating.model.Field;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.Section;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.templating.mapper.SubmissionDocumentMapper.toSections;

class SubmissionDocumentMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}

	@Test
	void sectionsFollowTheUiSectionsWithTheFieldsInTheirOrder() {
		final var schema = json("""
			{"properties": {
			  "a": {"type": "string", "title": "A"},
			  "b": {"type": "string", "title": "B"},
			  "c": {"type": "string", "title": "C"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [
			  {"id": "one", "title": "Första", "fields": ["b", "a"]},
			  {"id": "two", "title": "Andra", "fields": ["c"]}]}
			""");
		final var answers = json("""
			{"a": "1", "b": "2", "c": "3"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Första", List.of(Field.value("B", "2"), Field.value("A", "1"))),
			new Section("Andra", List.of(Field.value("C", "3"))));
	}

	@Test
	void fieldsWithoutAnAnswerOrWithAnEmptyAnswerAreLeftOut() {
		final var schema = json("""
			{"properties": {
			  "missing": {"type": "string", "title": "Missing"},
			  "nullValue": {"type": "string", "title": "Null"},
			  "emptyText": {"type": "string", "title": "Empty text"},
			  "emptyArray": {"type": "array", "title": "Empty array", "items": {"type": "string"}},
			  "emptyObject": {"type": "object", "title": "Empty object", "properties": {"x": {"type": "string"}}},
			  "answered": {"type": "string", "title": "Answered"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["missing", "nullValue", "emptyText", "emptyArray", "emptyObject", "answered"]}]}
			""");
		final var answers = json("""
			{"nullValue": null, "emptyText": "", "emptyArray": [], "emptyObject": {}, "answered": "yes"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(Field.value("Answered", "yes"))));
	}

	@Test
	void aSectionWithoutAnyAnsweredFieldIsLeftOut() {
		final var schema = json("""
			{"properties": {"a": {"type": "string", "title": "A"}, "b": {"type": "string", "title": "B"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Empty", "fields": ["a"]}, {"title": "Full", "fields": ["b"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"b\": \"x\"}"))).containsExactly(
			new Section("Full", List.of(Field.value("B", "x"))));
	}

	@Test
	void theLabelOfAFieldIsTheTitleInTheSchema() {
		final var schema = json("""
			{"properties": {"serveringsstalletsNamn": {"type": "string", "title": "Försäljningsställets namn"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["serveringsstalletsNamn"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"serveringsstalletsNamn\": \"Kiosken\"}"))).containsExactly(
			new Section("S", List.of(Field.value("Försäljningsställets namn", "Kiosken"))));
	}

	@Test
	void aValueWithOneOfIsShownAsTheTitleOfTheMatchingConst() {
		final var schema = json("""
			{"properties": {"firmatecknare": {"type": "string", "title": "Är du firmatecknare?",
			  "oneOf": [{"const": "JA", "title": "Ja"}, {"const": "NEJ", "title": "Nej"}]}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["firmatecknare"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"firmatecknare\": \"JA\"}"))).containsExactly(
			new Section("S", List.of(Field.value("Är du firmatecknare?", "Ja"))));
		assertThat(toSections(schema, uiSchema, json("{\"firmatecknare\": \"NEJ\"}"))).containsExactly(
			new Section("S", List.of(Field.value("Är du firmatecknare?", "Nej"))));
	}

	@Test
	void aValueWithOneOfButNoMatchingConstIsShownAsItIs() {
		final var schema = json("""
			{"properties": {"choice": {"type": "string", "title": "Choice", "oneOf": [{"const": "JA", "title": "Ja"}]}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["choice"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"choice\": \"KANSKE\"}"))).containsExactly(
			new Section("S", List.of(Field.value("Choice", "KANSKE"))));
	}

	@Test
	void anArrayWithItemsOneOfBecomesAListOfTheMatchingTitles() {
		final var schema = json("""
			{"properties": {"docs": {"type": "array", "title": "Dokumentation",
			  "items": {"type": "string", "oneOf": [
			    {"const": "A", "title": "Alfa"}, {"const": "B", "title": "Beta"}, {"const": "C", "title": "Gamma"}]}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["docs"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"docs\": [\"C\", \"A\"]}"))).containsExactly(
			new Section("S", List.of(Field.list("Dokumentation", List.of("Gamma", "Alfa")))));
	}

	@Test
	void anArrayOfObjectsBecomesATableWithTheColumnsInTheOrderOfItemsUiOrder() {
		final var schema = json("""
			{"properties": {"occasions": {"type": "array", "title": "Tillfällen",
			  "items": {"type": "object", "properties": {
			    "date": {"type": "string", "title": "Datum"},
			    "place": {"type": "string", "title": "Plats"},
			    "kind": {"type": "string", "title": "Typ", "oneOf": [{"const": "INNE", "title": "Inomhus"}, {"const": "UTE", "title": "Utomhus"}]}}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["occasions"]}],
			 "occasions": {"items": {"ui:order": ["place", "kind", "date"]}}}
			""");
		final var answers = json("""
			{"occasions": [
			  {"date": "2026-06-01", "place": "Torget", "kind": "UTE"},
			  {"date": "2026-06-02", "kind": "INNE"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(Field.table("Tillfällen", List.of("Plats", "Typ", "Datum"), List.of(
				List.of("Torget", "Utomhus", "2026-06-01"),
				List.of("", "Inomhus", "2026-06-02"))))));
	}

	@Test
	void anObjectWithSeveralFieldsBecomesAGroupInTheOrderOfItsUiOrder() {
		final var schema = json("""
			{"properties": {"fakturamottagare": {"type": "object", "title": "Fakturamottagare", "properties": {
			  "cO": {"type": "string", "title": "c/o"},
			  "utdelningsadress": {"type": "string", "title": "Utdelningsadress"},
			  "postnummer": {"type": "string", "title": "Postnummer"},
			  "postort": {"type": "string", "title": "Postort"}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["fakturamottagare"]}],
			 "fakturamottagare": {"ui:order": ["cO", "utdelningsadress", "postnummer", "postort"]}}
			""");
		final var answers = json("""
			{"fakturamottagare": {"postort": "Sundsvall", "postnummer": "851 85", "utdelningsadress": "Storgatan 1", "cO": ""}}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(Field.group("Fakturamottagare", List.of(
				new GroupField("Utdelningsadress", "Storgatan 1"),
				new GroupField("Postnummer", "851 85"),
				new GroupField("Postort", "Sundsvall"))))));
	}

	@Test
	void anObjectNestedInAGroupIsFlattenedIntoFieldsOfTheGroup() {
		final var schema = json("""
			{"properties": {"contact": {"type": "object", "title": "Kontakt", "properties": {
			  "name": {"type": "string", "title": "Namn"},
			  "address": {"type": "object", "title": "Adress", "properties": {
			    "street": {"type": "string", "title": "Gata"},
			    "city": {"type": "string", "title": "Ort"}}}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["contact"]}]}
			""");
		final var answers = json("""
			{"contact": {"name": "Anna", "address": {"street": "Storgatan 1", "city": "Sundsvall"}}}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(Field.group("Kontakt", List.of(
				new GroupField("Namn", "Anna"),
				new GroupField("Gata", "Storgatan 1"),
				new GroupField("Ort", "Sundsvall"))))));
	}

	@Test
	void anObjectWithASingleAnsweredFieldBecomesAPlainValue() {
		final var schema = json("""
			{"properties": {"kontakt": {"type": "object", "title": "Kontaktuppgifter", "properties": {
			  "telefonnummer": {"type": "string", "title": "Telefonnummer"},
			  "hemsida": {"type": "string", "title": "Hemsida"}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["kontakt"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"kontakt\": {\"telefonnummer\": \"060-123456\"}}"))).containsExactly(
			new Section("S", List.of(Field.value("Kontaktuppgifter", "060-123456"))));
	}

	@Test
	void aTimeIsShownAsHoursAndMinutesAlsoInAGroupAndATable() {
		final var schema = json("""
			{"properties": {
			  "start": {"type": "string", "title": "Start", "format": "time"},
			  "opening": {"type": "object", "title": "Öppettider", "properties": {
			    "from": {"type": "string", "title": "Från", "format": "time"},
			    "to": {"type": "string", "title": "Till", "format": "time"}}},
			  "occasions": {"type": "array", "title": "Tillfällen", "items": {"type": "object", "properties": {
			    "day": {"type": "string", "title": "Dag"},
			    "time": {"type": "string", "title": "Tid", "format": "time"}}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["start", "opening", "occasions"]}]}
			""");
		final var answers = json("""
			{"start": "12:12:00+02:00",
			 "opening": {"from": "08:05:00+01:00", "to": "17:30:00"},
			 "occasions": [{"day": "Måndag", "time": "23:59:59+02:00"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(
				Field.value("Start", "12:12"),
				Field.group("Öppettider", List.of(new GroupField("Från", "08:05"), new GroupField("Till", "17:30"))),
				Field.table("Tillfällen", List.of("Dag", "Tid"), List.of(List.of("Måndag", "23:59"))))));
	}

	@Test
	void aTimeWithAnOffsetIsShownAsHoursAndMinutesEvenWithoutFormatInTheSchema() {
		final var schema = json("""
			{"properties": {"start": {"type": "string", "title": "Start"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["start"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"start\": \"12:12:00+02:00\"}"))).containsExactly(
			new Section("S", List.of(Field.value("Start", "12:12"))));
	}

	@Test
	void aFieldWithTheTexteditorWidgetBecomesHtml() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Verksamhetsbeskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"beskrivning\": \"<p>Vi säljer <strong>folköl</strong></p>\"}"))).containsExactly(
			new Section("S", List.of(Field.html("Verksamhetsbeskrivning", "<p>Vi säljer <strong>folköl</strong></p>"))));
	}

	@Test
	void htmlIsStrippedOfImagesLinksStylesAndAttributesButKeepsFormattingAndText() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("beskrivning",
			"<style>p { background: url(http://evil.example/x) }</style>"
				+ "<p class=\"x\" style=\"background:url(http://evil.example/y)\" onclick=\"alert(1)\">Hej <em>du</em></p>"
				+ "<img src=\"http://evil.example/z.png\">"
				+ "<ul><li><a href=\"http://evil.example/\">länk</a></li></ul>"
				+ "<script>alert(1)</script>"
				+ "<p>1 &lt; 2 &amp; 3 > 2<br>rad</p>");

		final var sections = toSections(schema, uiSchema, answers);

		assertThat(sections).containsExactly(
			new Section("S", List.of(Field.html("Beskrivning", "<p>Hej <em>du</em></p><ul><li>länk</li></ul><p>1 &lt; 2 &amp; 3 &gt; 2<br/>rad</p>"))));
		final var html = sections.getFirst().fields().getFirst().html();
		assertThat(html).doesNotContain("evil", "<img", "<a", "<style", "<script", "class=", "style=", "onclick", "alert");
	}

	@Test
	void htmlThatIsNotATagIsEscaped() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("beskrivning", "<p>a < b & c</p><!-- <img src=x> --><p data-x=\"1\">d</p>");

		assertThat(toSections(schema, uiSchema, answers).getFirst().fields().getFirst().html())
			.isEqualTo("<p>a &lt; b &amp; c</p>&lt;!--  --&gt;<p>d</p>");
	}

	@Test
	void aPlainTextFieldWithHtmlCharactersStaysAPlainValueWithTheTextUnchanged() {
		final var schema = json("""
			{"properties": {"namn": {"type": "string", "title": "Namn"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "S", "fields": ["namn"]}],
			 "namn": {"ui:widget": "TextWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("namn", "<b>Kiosk & Co</b> <img src=x>");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("S", List.of(Field.value("Namn", "<b>Kiosk & Co</b> <img src=x>"))));
		assertThat(toSections(schema, uiSchema, answers).getFirst().fields().getFirst().html()).isNull();
	}

	@Test
	void theFolkolNotificationBecomesItsTwoSectionsWithTheExpectedFields() throws IOException {
		final var schema = readFixture("2281_aot_alcohol_folkol_sales_notification_0.11.schema.json");
		final var uiSchema = readFixture("2281_aot_alcohol_folkol_sales_notification_0.11.ui-schema.json");
		final var answers = json("""
			{
			  "arDuFirmatecknare": "JA",
			  "fakturaadressSammaSomForetaget": "NEJ",
			  "fakturamottagare": {"utdelningsadress": "Storgatan 1", "postnummer": "851 85", "postort": "Sundsvall"},
			  "fakturareferens": "<p>Ref <strong>123</strong></p>",
			  "verksamhetsbeskrivningsval": "JAG_VILL_SKRIVA_VERKSAMHETSBESKRIVNINGEN_I",
			  "verksamhetsbeskrivning": "<p>Vi säljer folköl</p>",
			  "stadigvarandeForsaljningAvLivsmedel": "JA",
			  "tillverkarFolkolPaForsaljningsstallet": "NEJ",
			  "dokumentationKanVisasVidTillsyn": ["JAG_KAN_VISA_DOKUMENTATION"],
			  "egenkontrollprogramFolkol": "NEJ_JAG_FYLLER_I_DOKUMENTET",
			  "serveringsstalletsNamn": "Kiosken",
			  "besoksadressSammaSomArendeagare": "JA",
			  "kontaktuppgifterTillServeringsstallet": {"telefonnummer": "060-123456", "ePostadress": "kiosk@example.com"}
			}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företag och fakturering", List.of(
				Field.value("Är du firmatecknare?", "Ja"),
				Field.value("Är fakturaadressen samma som företagets adress?", "Nej"),
				Field.group("Fakturamottagare", List.of(
					new GroupField("Utdelningsadress", "Storgatan 1"),
					new GroupField("Postnummer", "851 85"),
					new GroupField("Postort", "Sundsvall"))),
				Field.html("Fakturareferens", "<p>Ref <strong>123</strong></p>"),
				Field.value("Verksamhetsbeskrivning", "Jag vill skriva verksamhetsbeskrivningen i formuläret"),
				Field.html("Verksamhetsbeskrivning", "<p>Vi säljer folköl</p>"))),
			new Section("Försäljning av folköl (klass 2)", List.of(
				Field.value("Kommer du att ha stadigvarande försäljning av livsmedel?", "Ja"),
				Field.value("Kommer du att tillverka folköl på försäljningsstället?", "Nej"),
				Field.list("Dokumentation vid tillsyn", List.of("Jag kan visa dokumentation om detta vid tillsyn")),
				Field.value("Egenkontrollprogram vid försäljning av folköl (klass 2)", "Nej, det finns ett egenkontrollprogram och det kan visas vid tillsyn"),
				Field.value("Försäljningsställets namn", "Kiosken"),
				Field.value("Är försäljningsställets besöksadress samma som företagets adress?", "Ja"),
				Field.group("Kontaktuppgifter till försäljningsstället", List.of(
					new GroupField("Telefonnummer", "060-123456"),
					new GroupField("E-postadress", "kiosk@example.com"))))));
	}

	private static JsonNode readFixture(final String name) throws IOException {
		try (final var in = new ClassPathResource("submission/" + name).getInputStream()) {
			return JSON.readTree(in).get("value");
		}
	}
}
