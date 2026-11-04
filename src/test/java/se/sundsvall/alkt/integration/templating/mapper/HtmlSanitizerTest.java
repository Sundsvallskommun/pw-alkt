package se.sundsvall.alkt.integration.templating.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.templating.mapper.HtmlSanitizer.sanitize;
import static se.sundsvall.alkt.integration.templating.mapper.HtmlSanitizer.toPlainTextLines;

class HtmlSanitizerTest {

	@Test
	void toPlainTextLinesLeavesOutTheMarkupOfAnAnswerPastedFromOutlook() {
		final var answer = "<!--[if gte mso 9]><xml><o:OfficeDocumentSettings></o:OfficeDocumentSettings></xml><![endif]--><p class=MsoNormal>Inget att erinra<o:p>&nbsp;</o:p></p>";

		assertThat(toPlainTextLines(answer)).isEqualTo("Inget att erinra");
	}

	@Test
	void sanitizeLeavesOutTheMarkupOfAnAnswerPastedFromOutlook() {
		final var answer = "<!--[if gte mso 9]><xml></xml><![endif]--><p class=MsoNormal>Inget att erinra<o:p>&nbsp;</o:p></p>";

		assertThat(sanitize(answer)).isEqualTo("<p>Inget att erinra&nbsp;</p>");
	}

	@Test
	void sanitizeKeepsBlocksAndTableCellsApart() {
		assertThat(sanitize("<div>Kommunicerat 2026-10-20</div><div>Inga synpunkter</div>")).isEqualTo("Kommunicerat 2026-10-20<br/>Inga synpunkter<br/>");
		assertThat(sanitize("<table><tr><td>Kommunicerat</td><td>2026-10-20</td></tr></table>")).isEqualTo("Kommunicerat 2026-10-20 <br/>");
	}

	@Test
	void toPlainTextLinesPutsEachListItemOnALineOfItsOwn() {
		assertThat(toPlainTextLines("<ul><li>Ordningsvakter fredag och lördag</li><li>Kameraövervakning i entrén</li></ul>"))
			.isEqualTo("Ordningsvakter fredag och lördag\nKameraövervakning i entrén");
	}
}
