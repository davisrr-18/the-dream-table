package com.davisr.thedreamtable.client.feedback;

import java.util.Locale;

/**
 * Builds the JSON body the feedback Worker accepts. Text limits count UTF-16 units, the length JavaScript reports, so
 * the client and the Worker accept the same texts.
 */
public final class FeedbackJson {
	public static final int MIN_LENGTH = 10;
	public static final int MAX_LENGTH = 2000;
	/** The longest version or loader name the Worker accepts; longer values are cut. */
	public static final int FIELD_MAX = 32;
	private static final String UNKNOWN = "unknown";

	private FeedbackJson() {
	}

	/** The text with every line break as {@code \n} and the surrounding whitespace removed. */
	public static String cleanText(String raw) {
		return raw.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	public static boolean validText(String cleaned) {
		return cleaned.length() >= MIN_LENGTH && cleaned.length() <= MAX_LENGTH;
	}

	/** A version or loader name: any character outside letters, digits, dot, underscore, plus and dash becomes an underscore. */
	public static String field(String raw) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < raw.length() && builder.length() < FIELD_MAX; index++) {
			char character = raw.charAt(index);
			builder.append(isFieldCharacter(character) ? character : '_');
		}
		return builder.isEmpty() ? UNKNOWN : builder.toString();
	}

	/** The request body; throws when the text is outside the limits, which the form checks before it sends. */
	public static String body(String rawText, FeedbackCategory category, String modVersion, String loader, String mcVersion) {
		String text = cleanText(rawText);
		if (!validText(text)) {
			throw new IllegalArgumentException("Feedback text must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
		}
		return "{\"text\":" + quote(text)
				+ ",\"category\":" + quote(category.wire())
				+ ",\"modVersion\":" + quote(field(modVersion))
				+ ",\"loader\":" + quote(field(loader))
				+ ",\"mcVersion\":" + quote(field(mcVersion))
				+ "}";
	}

	/** A JSON string literal. Lone surrogates become U+FFFD, since they cannot be written as UTF-8. */
	static String quote(String value) {
		StringBuilder out = new StringBuilder(value.length() + 2).append('"');
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (Character.isHighSurrogate(character) && index + 1 < value.length()
					&& Character.isLowSurrogate(value.charAt(index + 1))) {
				out.append(character).append(value.charAt(++index));
			} else if (Character.isSurrogate(character)) {
				out.append('�');
			} else {
				appendEscaped(out, character);
			}
		}
		return out.append('"').toString();
	}

	private static void appendEscaped(StringBuilder out, char character) {
		switch (character) {
			case '"' -> out.append("\\\"");
			case '\\' -> out.append("\\\\");
			case '\n' -> out.append("\\n");
			case '\r' -> out.append("\\r");
			case '\t' -> out.append("\\t");
			case '\b' -> out.append("\\b");
			case '\f' -> out.append("\\f");
			default -> {
				if (character < 0x20) {
					out.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
				} else {
					out.append(character);
				}
			}
		}
	}

	private static boolean isFieldCharacter(char character) {
		return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
				|| (character >= '0' && character <= '9') || character == '.' || character == '_'
				|| character == '+' || character == '-';
	}
}
