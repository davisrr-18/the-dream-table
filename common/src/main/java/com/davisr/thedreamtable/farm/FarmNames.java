package com.davisr.thedreamtable.farm;

import org.jetbrains.annotations.Nullable;

/** Validates global custom farm names before they reach persistent state. */
public final class FarmNames {
	public static final int MAX_CODE_POINTS = 32;

	public enum Result {
		VALID,
		RESET,
		TOO_LONG,
		INVALID_CHARACTER,
		INVALID_UNICODE
	}

	public record Validation(Result result, @Nullable String name) {
	}

	private FarmNames() {
	}

	public static Validation validate(String input) {
		if (input == null) {
			return new Validation(Result.INVALID_UNICODE, null);
		}
		String name = trimInvisible(input);
		if (name.isEmpty()) {
			return new Validation(Result.RESET, null);
		}
		for (int index = 0; index < name.length(); index++) {
			char character = name.charAt(index);
			if (Character.isHighSurrogate(character)) {
				if (index + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(index + 1))) {
					return new Validation(Result.INVALID_UNICODE, null);
				}
				index++;
			} else if (Character.isLowSurrogate(character)) {
				return new Validation(Result.INVALID_UNICODE, null);
			}
		}
		if (name.codePointCount(0, name.length()) > MAX_CODE_POINTS) {
			return new Validation(Result.TOO_LONG, null);
		}
		if (name.codePoints().anyMatch(codePoint -> codePoint == '§' || Character.isISOControl(codePoint))) {
			return new Validation(Result.INVALID_CHARACTER, null);
		}
		return new Validation(Result.VALID, name);
	}

	/**
	 * Like strip(), but also drops no-break spaces at the ends; a name left with only blank-looking characters (format
	 * characters such as U+200B, Hangul and Braille fillers) is empty. Format characters inside or at the end of a visible
	 * name stay, since emoji flags and right-to-left names need them.
	 */
	private static String trimInvisible(String input) {
		int start = 0;
		int end = input.length();
		while (start < end && spacing(input.codePointAt(start))) {
			start += Character.charCount(input.codePointAt(start));
		}
		while (end > start && spacing(input.codePointBefore(end))) {
			end -= Character.charCount(input.codePointBefore(end));
		}
		String name = input.substring(start, end);
		return name.codePoints().allMatch(codePoint -> spacing(codePoint) || blank(codePoint)) ? "" : name;
	}

	private static boolean spacing(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

	private static boolean blank(int codePoint) {
		return Character.getType(codePoint) == Character.FORMAT || codePoint == 0x115F || codePoint == 0x1160 || codePoint == 0x2800
				|| codePoint == 0x3164 || codePoint == 0xFFA0;
	}
}
