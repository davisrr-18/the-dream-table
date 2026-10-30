package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmInfoTest {
	private static final Pattern INFO_KEY = Pattern.compile("farm\\.the_dream_table\\.(.+)\\.info\\.(.+)");

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void keysFollowTheDocumentedScheme() {
		assertEquals("farm.the_dream_table.iron_farm.info.how", FarmInfo.Section.HOW.key("iron_farm"));
		assertEquals("farm.the_dream_table.iron_farm.info.works", FarmInfo.Section.WORKS.key("iron_farm"));
		assertEquals("farm.the_dream_table.iron_farm.info.output", FarmInfo.Section.OUTPUT.key("iron_farm"));
		assertEquals("farm.the_dream_table.iron_farm.info.tips", FarmInfo.Section.TIPS.key("iron_farm"));
	}

	@Test
	void returnsOnlyTheSectionsTheLanguageHasInReadingOrder() {
		Map<String, String> lang = Map.of(
				"farm.the_dream_table.demo.info.tips", "Tip one",
				"farm.the_dream_table.demo.info.how", "It works",
				"farm.the_dream_table.demo.info.output", "Stuff");
		List<FarmInfo.Block> sections = FarmInfo.sections("demo", lang::get);
		assertEquals(List.of(FarmInfo.Section.HOW, FarmInfo.Section.OUTPUT, FarmInfo.Section.TIPS),
				sections.stream().map(FarmInfo.Block::section).toList());
		assertTrue(FarmInfo.hasInfo("demo", lang::get));
	}

	@Test
	void splitsAtLineFeedsAndDropsBlankLines() {
		Map<String, String> lang = Map.of("farm.the_dream_table.demo.info.how", "  First  \n\nSecond\n   \nThird\n");
		assertEquals(List.of("First", "Second", "Third"), FarmInfo.sections("demo", lang::get).getFirst().bullets());
	}

	@Test
	void aSectionWithOnlyBlankTextCountsAsMissing() {
		Map<String, String> lang = Map.of("farm.the_dream_table.demo.info.how", " \n ");
		assertTrue(FarmInfo.sections("demo", lang::get).isEmpty());
		assertFalse(FarmInfo.hasInfo("demo", lang::get));
	}

	@Test
	void unknownEmptyAndNullFarmsHaveNoInfo() {
		Map<String, String> lang = Map.of("farm.the_dream_table.demo.info.how", "It works");
		assertTrue(FarmInfo.sections("other", lang::get).isEmpty());
		assertFalse(FarmInfo.hasInfo("other", lang::get));
		assertFalse(FarmInfo.hasInfo("", lang::get));
		assertFalse(FarmInfo.hasInfo(null, lang::get));
		assertTrue(FarmInfo.sections(null, lang::get).isEmpty());
		assertFalse(FarmInfo.hasInfo("imported_by_a_player"));
	}

	@Test
	void everyInfoKeyInTheLanguageFilesBelongsToABuiltInFarmAndAKnownSection() throws IOException {
		Set<String> farms = FarmOptions.ALL.stream().map(FarmOptions.FarmOption::id).collect(Collectors.toSet());
		Set<String> sections = Arrays.stream(FarmInfo.Section.values())
				.map(section -> section.key("x").substring("farm.the_dream_table.x.info.".length())).collect(Collectors.toSet());
		for (String code : List.of("en_us", "pt_br")) {
			JsonObject lang = lang(code);
			for (String key : lang.keySet()) {
				Matcher matcher = INFO_KEY.matcher(key);
				if (!matcher.matches()) {
					continue;
				}
				assertTrue(farms.contains(matcher.group(1)), code + " " + key + ": not a built-in farm");
				assertTrue(sections.contains(matcher.group(2)), code + " " + key + ": not a section");
				assertFalse(FarmInfo.bullets(lang.get(key).getAsString()).isEmpty(), code + " " + key + ": empty");
			}
		}
	}

	private static JsonObject lang(String code) throws IOException {
		try (InputStream stream = FarmInfoTest.class.getResourceAsStream("/assets/the_dream_table/lang/" + code + ".json")) {
			assertNotNull(stream, code + ".json is missing");
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
