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
		String name = input.strip();
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
}
