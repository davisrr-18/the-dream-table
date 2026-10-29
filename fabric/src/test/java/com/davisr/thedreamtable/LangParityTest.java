package com.davisr.thedreamtable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LangParityTest {
	private static final String[] TRANSLATIONS = {"pt_br"};
	private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd%]");

	private static JsonObject lang(String code) throws IOException {
		try (InputStream stream = LangParityTest.class.getResourceAsStream("/assets/the_dream_table/lang/" + code + ".json")) {
			assertNotNull(stream, code + ".json is missing");
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static List<String> placeholders(String text) {
		List<String> found = new ArrayList<>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			found.add(matcher.group());
		}
		return found;
	}

	@Test
	void everyTranslationHasExactlyTheEnglishKeys() throws IOException {
		JsonObject english = lang("en_us");
		for (String code : TRANSLATIONS) {
			assertEquals(english.keySet(), lang(code).keySet(), code);
		}
	}

	@Test
	void everyTranslationKeepsTheEnglishPlaceholders() throws IOException {
		JsonObject english = lang("en_us");
		for (String code : TRANSLATIONS) {
			JsonObject translated = lang(code);
			for (String key : english.keySet()) {
				// Missing keys are reported by the key test.
				if (!translated.has(key)) {
					continue;
				}
				assertEquals(placeholders(english.get(key).getAsString()), placeholders(translated.get(key).getAsString()), code + " " + key);
			}
		}
	}
}
