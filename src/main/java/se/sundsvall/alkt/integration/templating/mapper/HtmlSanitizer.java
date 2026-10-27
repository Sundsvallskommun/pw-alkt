package se.sundsvall.alkt.integration.templating.mapper;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.web.util.HtmlUtils.htmlUnescape;

/** Strips the html of a rich-text answer down to bare formatting tags and escapes the rest, or down to its text. */
public final class HtmlSanitizer {

	// The template writes html unescaped, so only bare formatting tags survive: no images, links, styles or attributes that
	// could make templating fetch an outside address.
	private static final Set<String> ALLOWED_TAGS = Set.of("p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li", "blockquote",
		"h1", "h2", "h3", "h4", "h5", "h6", "sub", "sup");
	// Why: an opener without its closer swallows the rest of the input in one match, which keeps the scan linear.
	private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style)\\b[^>]*+(?:>.*?(?:</\\1\\s*>|\\z)|\\z)");
	private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)(?:\\s[^<>]*)?/?>");
	private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)");

	private HtmlSanitizer() {}

	public static String sanitize(final String html) {
		final var text = SCRIPT_OR_STYLE.matcher(html).replaceAll("");
		final var sanitized = new StringBuilder();
		final var matcher = TAG.matcher(text);
		var last = 0;
		while (matcher.find()) {
			sanitized.append(escape(text.substring(last, matcher.start())));
			final var tag = matcher.group(2).toLowerCase(Locale.ROOT);
			if ("br".equals(tag)) {
				sanitized.append("<br/>");
			} else if (ALLOWED_TAGS.contains(tag)) {
				sanitized.append('<').append(matcher.group(1)).append(tag).append('>');
			}
			last = matcher.end();
		}
		return sanitized.append(escape(text.substring(last))).toString();
	}

	/** The text of a rich-text answer without any tags, for a place in the template that shows plain values only. */
	public static String toPlainText(final String html) {
		final var text = TAG.matcher(SCRIPT_OR_STYLE.matcher(html).replaceAll("")).replaceAll(" ");
		return htmlUnescape(text).replaceAll("\\s+", " ").strip();
	}

	private static String escape(final String text) {
		return BARE_AMPERSAND.matcher(text).replaceAll("&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
