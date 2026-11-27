package com.davisr.thedreamtable.farm.capture;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Turns what the player typed into a file name that can only be a plain name inside the structures folder. */
public final class CaptureName {
	public static final int MAX_LENGTH = 64;
	/** Names a Windows file system reserves, with or without an extension. */
	private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul",
			"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
			"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private CaptureName() {
	}

	/**
	 * The name as it will be saved, or null when nothing usable is left: accents are folded to their letter, spaces become
	 * underscores, and every other character outside {@code a-z 0-9 _ -} is dropped, so a dot, a slash or a drive letter's
	 * colon can never reach the path.
	 */
	public static String sanitize(String typed) {
		if (typed == null || typed.length() > MAX_LENGTH) {
			return null;
		}
		String folded = Normalizer.normalize(typed.trim(), Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
		StringBuilder name = new StringBuilder();
		for (int index = 0; index < folded.length(); index++) {
			char letter = folded.charAt(index);
			if (letter == ' ') {
				name.append('_');
			} else if ((letter >= 'a' && letter <= 'z') || (letter >= '0' && letter <= '9') || letter == '_' || letter == '-') {
				name.append(letter);
			}
		}
		String result = name.toString();
		return result.isEmpty() || RESERVED.contains(result) ? null : result;
	}
}
