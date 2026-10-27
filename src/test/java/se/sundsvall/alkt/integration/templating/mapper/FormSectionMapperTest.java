package se.sundsvall.alkt.integration.templating.mapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import se.sundsvall.alkt.integration.templating.model.GroupEntry;
import se.sundsvall.alkt.integration.templating.model.GroupField;
import se.sundsvall.alkt.integration.templating.model.HtmlField;
import se.sundsvall.alkt.integration.templating.model.ListField;
import se.sundsvall.alkt.integration.templating.model.Section;
import se.sundsvall.alkt.integration.templating.model.TableField;
import se.sundsvall.alkt.integration.templating.model.TableField.Row;
import se.sundsvall.alkt.integration.templating.model.ValueField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static se.sundsvall.alkt.integration.templating.mapper.FormSectionMapper.toSections;

class FormSectionMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static JsonNode json(final String text) {
		return JSON.readTree(text);
	}

	@Test
	void sectionsFollowTheUiSectionsWithTheFieldsInTheirOrder() {
		final var schema = json("""
			{"properties": {
			  "namn": {"type": "string", "title": "Namn"},
			  "telefon": {"type": "string", "title": "Telefon"},
			  "ort": {"type": "string", "title": "Ort"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [
			  {"id": "foretaget", "title": "Företaget", "fields": ["telefon", "namn"]},
			  {"id": "forsaljningsstallet", "title": "Försäljningsstället", "fields": ["ort"]}]}
			""");
		final var answers = json("""
			{"namn": "Kiosken", "telefon": "060-123456", "ort": "Sundsvall"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Telefon", "060-123456"), new ValueField("Namn", "Kiosken"))),
			new Section("Försäljningsstället", List.of(new ValueField("Ort", "Sundsvall"))));
	}

	@Test
	void fieldsWithoutAnAnswerOrWithAnEmptyAnswerAreLeftOut() {
		final var schema = json("""
			{"properties": {
			  "missing": {"type": "string", "title": "Missing"},
			  "nullValue": {"type": "string", "title": "Null"},
			  "emptyText": {"type": "string", "title": "Empty text"},
			  "emptyArray": {"type": "array", "title": "Empty array", "items": {"type": "string"}},
			  "emptyObject": {"type": "object", "title": "Empty object", "properties": {"namn": {"type": "string"}}},
			  "answered": {"type": "string", "title": "Answered"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["missing", "nullValue", "emptyText", "emptyArray", "emptyObject", "answered"]}]}
			""");
		final var answers = json("""
			{"nullValue": null, "emptyText": "", "emptyArray": [], "emptyObject": {}, "answered": "yes"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Answered", "yes"))));
	}

	@Test
	void anObjectWhoseFieldsAreAllEmptyAlsoNestedIsLeftOut() {
		final var schema = json("""
			{"properties": {
			  "contact": {"type": "object", "title": "Contact", "properties": {
			    "name": {"type": "string"},
			    "address": {"type": "object", "properties": {"street": {"type": "string"}}}}},
			  "answered": {"type": "string", "title": "Answered"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["contact", "answered"]}]}
			""");
		final var answers = json("""
			{"contact": {"name": "", "address": {"street": null}}, "answered": "yes"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Answered", "yes"))));
	}

	@Test
	void aSectionWithoutAnyAnsweredFieldIsLeftOut() {
		final var schema = json("""
			{"properties": {"namn": {"type": "string", "title": "Namn"}, "telefon": {"type": "string", "title": "Telefon"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Empty", "fields": ["namn"]}, {"title": "Full", "fields": ["telefon"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"telefon\": \"060-123456\"}"))).containsExactly(
			new Section("Full", List.of(new ValueField("Telefon", "060-123456"))));
	}

	@Test
	void answeredFieldsNoSectionListsEndUpInALastSectionWithoutATitle() {
		final var schema = json("""
			{"properties": {
			  "namn": {"type": "string", "title": "Namn"},
			  "telefon": {"type": "string", "title": "Telefon"},
			  "epost": {"type": "string", "title": "E-post"},
			  "ort": {"type": "string", "title": "Ort"}}}
			""");
		final var uiSchema = json("""
			{"ui:order": ["ort", "*"], "ui:sections": [{"title": "Företaget", "fields": ["namn"]}]}
			""");
		final var answers = json("""
			{"namn": "Kiosken", "telefon": "060-123456", "epost": "", "ort": "Sundsvall"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Namn", "Kiosken"))),
			new Section("", List.of(new ValueField("Ort", "Sundsvall"), new ValueField("Telefon", "060-123456"))));
	}

	@Test
	void anAnswerMissingFromTheSchemaEndsUpInTheLastSectionLabelledByItsKey() {
		final var schema = json("""
			{"properties": {"namn": {"type": "string", "title": "Namn"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["namn"]}]}
			""");
		final var answers = json("""
			{"namn": "Kiosken", "undeclared": "Svar utanför schemat"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Namn", "Kiosken"))),
			new Section("", List.of(new ValueField("undeclared", "Svar utanför schemat"))));
	}

	@Test
	void withoutUiSectionsEveryAnsweredFieldEndsUpInOneSectionWithoutATitleInTheUiOrder() {
		final var schema = json("""
			{"properties": {
			  "namn": {"type": "string", "title": "Namn"},
			  "telefon": {"type": "string", "title": "Telefon"},
			  "ort": {"type": "string", "title": "Ort"}}}
			""");
		final var uiSchema = json("""
			{"ui:order": ["ort", "namn"]}
			""");
		final var answers = json("""
			{"namn": "Kiosken", "telefon": "060-123456", "ort": "Sundsvall"}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("", List.of(new ValueField("Ort", "Sundsvall"), new ValueField("Namn", "Kiosken"), new ValueField("Telefon", "060-123456"))));
	}

	@Test
	void noSectionWithoutATitleIsAddedWhenTheSectionsListEveryAnsweredField() {
		final var schema = json("""
			{"properties": {"namn": {"type": "string", "title": "Namn"}, "telefon": {"type": "string", "title": "Telefon"}}}
			""");
		final var uiSchema = json("""
			{"ui:order": ["telefon", "namn"], "ui:sections": [{"title": "Företaget", "fields": ["namn"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"namn\": \"Kiosken\"}"))).containsExactly(
			new Section("Företaget", List.of(new ValueField("Namn", "Kiosken"))));
	}

	@Test
	void theLabelOfAFieldIsTheTitleInTheSchema() {
		final var schema = json("""
			{"properties": {"serveringsstalletsNamn": {"type": "string", "title": "Försäljningsställets namn"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["serveringsstalletsNamn"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"serveringsstalletsNamn\": \"Kiosken\"}"))).containsExactly(
			new Section("Försäljningsstället", List.of(new ValueField("Försäljningsställets namn", "Kiosken"))));
	}

	@Test
	void aFieldWithoutATitleOrMissingFromTheSchemaInAGroupIsLabelledByItsKey() {
		final var schema = json("""
			{"properties": {"contact": {"type": "object", "title": "Kontakt", "properties": {
			  "namn": {"type": "string"}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["contact"]}]}
			""");
		final var answers = json("""
			{"contact": {"namn": "Anna", "telefon": "060-123456"}}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new GroupField("Kontakt", List.of(
				new GroupEntry("namn", "Anna"),
				new GroupEntry("telefon", "060-123456"))))));
	}

	@Test
	void aValueWithOneOfIsShownAsTheTitleOfTheMatchingConst() {
		final var schema = json("""
			{"properties": {"firmatecknare": {"type": "string", "title": "Är du firmatecknare?",
			  "oneOf": [{"const": "JA", "title": "Ja"}, {"const": "NEJ", "title": "Nej"}]}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["firmatecknare"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"firmatecknare\": \"JA\"}"))).containsExactly(
			new Section("Företaget", List.of(new ValueField("Är du firmatecknare?", "Ja"))));
		assertThat(toSections(schema, uiSchema, json("{\"firmatecknare\": \"NEJ\"}"))).containsExactly(
			new Section("Företaget", List.of(new ValueField("Är du firmatecknare?", "Nej"))));
	}

	@Test
	void aValueWithOneOfButNoMatchingConstIsShownAsItIs() {
		final var schema = json("""
			{"properties": {"choice": {"type": "string", "title": "Choice", "oneOf": [{"const": "JA", "title": "Ja"}]}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["choice"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"choice\": \"KANSKE\"}"))).containsExactly(
			new Section("Företaget", List.of(new ValueField("Choice", "KANSKE"))));
	}

	@Test
	void anArrayWithItemsOneOfBecomesAListOfTheMatchingTitles() {
		final var schema = json("""
			{"properties": {"docs": {"type": "array", "title": "Dokumentation",
			  "items": {"type": "string", "oneOf": [
			    {"const": "EGENKONTROLLPROGRAM", "title": "Egenkontrollprogram"},
			    {"const": "UTBILDNINGSBEVIS", "title": "Utbildningsbevis"},
			    {"const": "INKOPSKVITTON", "title": "Inköpskvitton"}]}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["docs"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"docs\": [\"INKOPSKVITTON\", \"EGENKONTROLLPROGRAM\"]}"))).containsExactly(
			new Section("Försäljningsstället", List.of(new ListField("Dokumentation", List.of("Inköpskvitton", "Egenkontrollprogram")))));
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
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["occasions"]}],
			 "occasions": {"items": {"ui:order": ["place", "kind", "date"]}}}
			""");
		final var answers = json("""
			{"occasions": [
			  {"date": "2026-06-01", "place": "Torget", "kind": "UTE"},
			  {"date": "2026-06-02", "kind": "INNE"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Försäljningsstället", List.of(new TableField("Tillfällen", List.of("Plats", "Typ", "Datum"), List.of(
				Row.of("Torget", "Utomhus", "2026-06-01"),
				Row.of("", "Inomhus", "2026-06-02"))))));
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
			{"ui:sections": [{"title": "Företaget", "fields": ["fakturamottagare"]}],
			 "fakturamottagare": {"ui:order": ["cO", "utdelningsadress", "postnummer", "postort"]}}
			""");
		final var answers = json("""
			{"fakturamottagare": {"postort": "Sundsvall", "postnummer": "851 85", "utdelningsadress": "Storgatan 1", "cO": ""}}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new GroupField("Fakturamottagare", List.of(
				new GroupEntry("Utdelningsadress", "Storgatan 1"),
				new GroupEntry("Postnummer", "851 85"),
				new GroupEntry("Postort", "Sundsvall"))))));
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
			{"ui:sections": [{"title": "Företaget", "fields": ["contact"]}]}
			""");
		final var answers = json("""
			{"contact": {"name": "Anna", "address": {"street": "Storgatan 1", "city": "Sundsvall"}}}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new GroupField("Kontakt", List.of(
				new GroupEntry("Namn", "Anna"),
				new GroupEntry("Gata", "Storgatan 1"),
				new GroupEntry("Ort", "Sundsvall"))))));
	}

	@Test
	void anObjectWithASingleAnsweredFieldBecomesAPlainValue() {
		final var schema = json("""
			{"properties": {"kontakt": {"type": "object", "title": "Kontaktuppgifter", "properties": {
			  "telefonnummer": {"type": "string", "title": "Telefonnummer"},
			  "hemsida": {"type": "string", "title": "Hemsida"}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["kontakt"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"kontakt\": {\"telefonnummer\": \"060-123456\"}}"))).containsExactly(
			new Section("Försäljningsstället", List.of(new ValueField("Kontaktuppgifter", "060-123456"))));
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
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["start", "opening", "occasions"]}]}
			""");
		final var answers = json("""
			{"start": "12:12:00+02:00",
			 "opening": {"from": "08:05:00+01:00", "to": "17:30:00"},
			 "occasions": [{"day": "Måndag", "time": "23:59:59+02:00"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Försäljningsstället", List.of(
				new ValueField("Start", "12:12"),
				new GroupField("Öppettider", List.of(new GroupEntry("Från", "08:05"), new GroupEntry("Till", "17:30"))),
				new TableField("Tillfällen", List.of("Dag", "Tid"), List.of(Row.of("Måndag", "23:59"))))));
	}

	@Test
	void aTimeWithAnOffsetIsShownAsHoursAndMinutesEvenWithoutFormatInTheSchema() {
		final var schema = json("""
			{"properties": {"start": {"type": "string", "title": "Start"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["start"]}]}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"start\": \"12:12:00+02:00\"}"))).containsExactly(
			new Section("Försäljningsstället", List.of(new ValueField("Start", "12:12"))));
	}

	@Test
	void aFieldWithTheTexteditorWidgetBecomesHtml() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Verksamhetsbeskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");

		assertThat(toSections(schema, uiSchema, json("{\"beskrivning\": \"<p>Vi säljer <strong>folköl</strong></p>\"}"))).containsExactly(
			new Section("Företaget", List.of(new HtmlField("Verksamhetsbeskrivning", "<p>Vi säljer <strong>folköl</strong></p>"))));
	}

	@Test
	void aFieldWithTheTexteditorWidgetInAGroupOrATableBecomesItsTextWithoutTags() {
		final var schema = json("""
			{"properties": {
			  "contact": {"type": "object", "title": "Kontakt", "properties": {
			    "name": {"type": "string", "title": "Namn"},
			    "comment": {"type": "string", "title": "Kommentar"}}},
			  "occasions": {"type": "array", "title": "Tillfällen", "items": {"type": "object", "properties": {
			    "comment": {"type": "string", "title": "Kommentar"}}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["contact", "occasions"]}],
			 "contact": {"comment": {"ui:widget": "TexteditorWidget"}},
			 "occasions": {"items": {"comment": {"ui:widget": "TexteditorWidget"}}}}
			""");
		final var answers = json("""
			{"contact": {"name": "Anna", "comment": "<p>Ref <strong>1</strong> &amp; Co</p><p>rad 2<script>alert(1)</script></p>"},
			 "occasions": [{"comment": "<p>Ref <strong>2</strong></p>"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Försäljningsstället", List.of(
				new GroupField("Kontakt", List.of(new GroupEntry("Namn", "Anna"), new GroupEntry("Kommentar", "Ref 1 & Co rad 2"))),
				new TableField("Tillfällen", List.of("Kommentar"), List.of(Row.of("Ref 2"))))));
	}

	@Test
	void aRowKeyMissingFromTheItemsSchemaBecomesAColumnLabelledByItsKey() {
		final var schema = json("""
			{"properties": {
			  "occasions": {"type": "array", "title": "Tillfällen", "items": {"type": "object", "properties": {
			    "date": {"type": "string", "title": "Datum"}}}},
			  "undeclared": {"type": "array"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Försäljningsstället", "fields": ["occasions", "undeclared"]}]}
			""");
		final var answers = json("""
			{"occasions": [{"date": "2026-06-01", "place": "Torget"}, {"date": "2026-06-02"}],
			 "undeclared": [{"namn": "Anna"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Försäljningsstället", List.of(
				new TableField("Tillfällen", List.of("Datum", "place"), List.of(Row.of("2026-06-01", "Torget"), Row.of("2026-06-02", ""))),
				new TableField("undeclared", List.of("namn"), List.of(Row.of("Anna"))))));
	}

	@Test
	void anArrayWhoseItemsAreAllEmptyIsLeftOutAndAnEmptyRowIsDropped() {
		final var schema = json("""
			{"properties": {
			  "emptyRows": {"type": "array", "title": "Tomma rader", "items": {"type": "object", "properties": {"namn": {"type": "string"}}}},
			  "blankRows": {"type": "array", "title": "Blanka rader", "items": {"type": "object", "properties": {"namn": {"type": "string"}}}},
			  "nullItems": {"type": "array", "title": "Null", "items": {"type": "string"}},
			  "blankItems": {"type": "array", "title": "Blanka", "items": {"type": "string"}},
			  "contact": {"type": "object", "title": "Kontakt", "properties": {
			    "name": {"type": "string", "title": "Namn"},
			    "phones": {"type": "array", "title": "Telefon", "items": {"type": "string"}}}},
			  "occasions": {"type": "array", "title": "Tillfällen", "items": {"type": "object", "properties": {"namn": {"type": "string", "title": "Namn"}}}}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["emptyRows", "blankRows", "nullItems", "blankItems", "contact", "occasions"]}]}
			""");
		final var answers = json("""
			{"emptyRows": [{}], "blankRows": [{"namn": ""}], "nullItems": [null], "blankItems": [""],
			 "contact": {"name": "Anna", "phones": [""]},
			 "occasions": [{"namn": ""}, {"namn": "Midsommar"}]}
			""");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(
				new ValueField("Kontakt", "Anna"),
				new TableField("Tillfällen", List.of("Namn"), List.of(Row.of("Midsommar"))))));
	}

	@Test
	void htmlIsStrippedOfImagesLinksStylesAndAttributesButKeepsFormattingAndText() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("beskrivning",
			"<style>p { background: url(http://evil.example/style.png) }</style>"
				+ "<p class=\"rubrik\" style=\"background:url(http://evil.example/background.png)\" onclick=\"alert(1)\">Hej <em>du</em></p>"
				+ "<img src=\"http://evil.example/bild.png\">"
				+ "<ul><li><a href=\"http://evil.example/\">länk</a></li></ul>"
				+ "<script>alert(1)</script>"
				+ "<p>1 &lt; 2 &amp; 3 > 2<br>rad</p>");

		final var sections = toSections(schema, uiSchema, answers);

		assertThat(sections).containsExactly(
			new Section("Företaget", List.of(new HtmlField("Beskrivning", "<p>Hej <em>du</em></p><ul><li>länk</li></ul><p>1 &lt; 2 &amp; 3 &gt; 2<br/>rad</p>"))));
		final var html = ((HtmlField) sections.getFirst().fields().getFirst()).html();
		assertThat(html).doesNotContain("evil", "<img", "<a", "<style", "<script", "class=", "style=", "onclick", "alert");
	}

	@Test
	void htmlThatIsNotATagIsEscaped() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("beskrivning", "<p>1 < 2 & 3</p><!-- <img src=bild.png> --><p data-id=\"1\">Hej</p>");

		assertThat(((HtmlField) toSections(schema, uiSchema, answers).getFirst().fields().getFirst()).html())
			.isEqualTo("<p>1 &lt; 2 &amp; 3</p>&lt;!--  --&gt;<p>Hej</p>");
	}

	@Test
	void aScriptOrStyleWithoutItsClosingTagDropsTheRestOfTheHtml() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");

		assertThat(((HtmlField) toSections(schema, uiSchema, JSON.createObjectNode().put("beskrivning", "<p>Hej</p><script>alert(1)<p>Mer</p>"))
			.getFirst().fields().getFirst()).html()).isEqualTo("<p>Hej</p>");
		assertThat(((HtmlField) toSections(schema, uiSchema, JSON.createObjectNode().put("beskrivning", "<p>Hej</p><style p { color: red }"))
			.getFirst().fields().getFirst()).html()).isEqualTo("<p>Hej</p>");
	}

	@Test
	void manyScriptTagsWithoutClosingTagsAreStrippedInLinearTime() {
		final var schema = json("""
			{"properties": {"beskrivning": {"type": "string", "title": "Beskrivning"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["beskrivning"]}],
			 "beskrivning": {"ui:widget": "TexteditorWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("beskrivning", "<p>Hej</p>" + "<script>".repeat(50_000));

		final var sections = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> toSections(schema, uiSchema, answers));

		assertThat(((HtmlField) sections.getFirst().fields().getFirst()).html()).isEqualTo("<p>Hej</p>");
	}

	@Test
	void aPlainTextFieldWithHtmlCharactersStaysAPlainValueWithTheTextUnchanged() {
		final var schema = json("""
			{"properties": {"namn": {"type": "string", "title": "Namn"}}}
			""");
		final var uiSchema = json("""
			{"ui:sections": [{"title": "Företaget", "fields": ["namn"]}],
			 "namn": {"ui:widget": "TextWidget"}}
			""");
		final var answers = JSON.createObjectNode().put("namn", "<b>Kiosk & Co</b> <img src=bild.png>");

		assertThat(toSections(schema, uiSchema, answers)).containsExactly(
			new Section("Företaget", List.of(new ValueField("Namn", "<b>Kiosk & Co</b> <img src=bild.png>"))));
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
				new ValueField("Är du firmatecknare?", "Ja"),
				new ValueField("Är fakturaadressen samma som företagets adress?", "Nej"),
				new GroupField("Fakturamottagare", List.of(
					new GroupEntry("Utdelningsadress", "Storgatan 1"),
					new GroupEntry("Postnummer", "851 85"),
					new GroupEntry("Postort", "Sundsvall"))),
				new HtmlField("Fakturareferens", "<p>Ref <strong>123</strong></p>"),
				new ValueField("Verksamhetsbeskrivning", "Jag vill skriva verksamhetsbeskrivningen i formuläret"),
				new HtmlField("Verksamhetsbeskrivning", "<p>Vi säljer folköl</p>"))),
			new Section("Försäljning av folköl (klass 2)", List.of(
				new ValueField("Kommer du att ha stadigvarande försäljning av livsmedel?", "Ja"),
				new ValueField("Kommer du att tillverka folköl på försäljningsstället?", "Nej"),
				new ListField("Dokumentation vid tillsyn", List.of("Jag kan visa dokumentation om detta vid tillsyn")),
				new ValueField("Egenkontrollprogram vid försäljning av folköl (klass 2)", "Nej, det finns ett egenkontrollprogram och det kan visas vid tillsyn"),
				new ValueField("Försäljningsställets namn", "Kiosken"),
				new ValueField("Är försäljningsställets besöksadress samma som företagets adress?", "Ja"),
				new GroupField("Kontaktuppgifter till försäljningsstället", List.of(
					new GroupEntry("Telefonnummer", "060-123456"),
					new GroupEntry("E-postadress", "kiosk@example.com"))))));
	}

	private static JsonNode readFixture(final String name) throws IOException {
		try (final var in = new ClassPathResource("submission/" + name).getInputStream()) {
			return JSON.readTree(in).get("value");
		}
	}
}
