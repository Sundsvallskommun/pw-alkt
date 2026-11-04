package se.sundsvall.alkt.integration.templating.mapper;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.web.util.HtmlUtils.htmlUnescape;

/** Strips the html of a rich-text answer down to bare formatting tags and escapes the rest, or down to its text. */
public final class HtmlSanitizer {

	// The template writes html unescaped, so only bare formatting tags survive: no images, links, styles or attributes that
	// could make templating fetch an outside address.
	private static final Set<String> ALLOWED_TAGS = Set.of("p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li", "blockquote",
		"h1", "h2", "h3", "h4", "h5", "h6", "sub", "sup");
	private static final String BR = "<br/>";
	private static final Map<String, String> SEPARATORS_OF_DROPPED_CLOSING_TAGS = Map.of("div", BR, "tr", BR, "td", " ", "th", " ");
	private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?(?:-->|\\z)");
	private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)\\b[^>]*+(?:>.*?(?:</\\1\\s*>|\\z)|\\z)");
	private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][\\w:-]*)(?:\\s[^<>]*)?/?>");
	private static final Pattern LINE_BREAK = Pattern.compile("(?i)</(?:p|div|li|h[1-6]|blockquote)\\s*>|<(?:br|ul|ol)\\b[^>]*>");
	private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)");

	private HtmlSanitizer() {}

	public static String sanitize(final String html) {
		final var text = withoutCommentsAndScripts(html);
		final var sanitized = new StringBuilder();
		final var matcher = TAG.matcher(text);
		var last = 0;
		while (matcher.find()) {
			sanitized.append(escape(text.substring(last, matcher.start())));
			final var tag = matcher.group(2).toLowerCase(Locale.ROOT);
			if ("br".equals(tag)) {
				sanitized.append(BR);
			} else if (ALLOWED_TAGS.contains(tag)) {
				sanitized.append('<').append(matcher.group(1)).append(tag).append('>');
			} else if ("/".equals(matcher.group(1))) {
				sanitized.append(SEPARATORS_OF_DROPPED_CLOSING_TAGS.getOrDefault(tag, ""));
			}
			last = matcher.end();
		}
		return sanitized.append(escape(text.substring(last))).toString();
	}

	/** The text of a rich-text answer without any tags, for a place in the template that shows plain values only. */
	public static String toPlainText(final String html) {
		final var text = TAG.matcher(withoutCommentsAndScripts(html)).replaceAll(" ");
		return htmlUnescape(text).replaceAll("\\s+", " ").strip();
	}

	/** The text of a rich-text answer without any tags, one line per paragraph, line break or list item. */
	public static String toPlainTextLines(final String html) {
		final var withoutScripts = withoutCommentsAndScripts(html);
		final var flowing = Optional.of(withoutScripts).filter(text -> TAG.matcher(text).find()).map(text -> text.replaceAll("\\s+", " ")).orElse(withoutScripts);
		final var text = TAG.matcher(LINE_BREAK.matcher(flowing).replaceAll("\n")).replaceAll(" ");
		return htmlUnescape(text).replace('\u00A0', ' ').lines()
			.map(line -> line.replaceAll("\\s+", " ").strip())
			.collect(Collectors.joining("\n"))
			.replaceAll("\n{3,}", "\n\n")
			.strip();
	}

	private static String withoutCommentsAndScripts(final String html) {
		return SCRIPT_OR_STYLE.matcher(COMMENT.matcher(html).replaceAll("")).replaceAll("");
	}

	private static String escape(final String text) {
		return BARE_AMPERSAND.matcher(text).replaceAll("&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
