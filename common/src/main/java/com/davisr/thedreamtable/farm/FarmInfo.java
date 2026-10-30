package com.davisr.thedreamtable.farm;

import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The help text of a built-in farm: how it works, when it works, what it produces and tips. The texts are not in code
 * but in the language files, under {@code farm.the_dream_table.<id>.info.<section>}, one key per section, with
 * several bullet lines separated by a line feed. Only the sections a language has are returned, and the game falls
 * back to English for keys another language lacks. Imported and unknown farms have no keys, hence no info.
 */
public final class FarmInfo {
	/** A section of the help, with the last part of its translation key and the key of its heading. */
	public enum Section {
		HOW("how", "gui.the_dream_table.info_how"),
		WORKS("works", "gui.the_dream_table.info_works"),
		OUTPUT("output", "gui.the_dream_table.info_output"),
		TIPS("tips", "gui.the_dream_table.info_tips");

		private final String suffix;
		private final String titleKey;

		Section(String suffix, String titleKey) {
			this.suffix = suffix;
			this.titleKey = titleKey;
		}

		public String titleKey() {
			return titleKey;
		}

		/** The translation key of this section's text for a farm. */
		public String key(String farmId) {
			return "farm.the_dream_table." + farmId + ".info." + suffix;
		}
	}

	/** One section of a farm's help, as bullet lines that are never blank. */
	public record Block(Section section, List<String> bullets) {
		public Block {
			bullets = List.copyOf(bullets);
		}
	}

	private FarmInfo() {
	}

	/** The sections the current language has for the farm, in reading order; empty for a farm without help. */
	public static List<Block> sections(String farmId) {
		return sections(farmId, FarmInfo::translation);
	}

	public static boolean hasInfo(String farmId) {
		return hasInfo(farmId, FarmInfo::translation);
	}

	/** {@code lookup} gives the text of a translation key, or null when there is none. */
	public static List<Block> sections(String farmId, Function<String, String> lookup) {
		List<Block> blocks = new ArrayList<>();
		if (farmId == null || farmId.isEmpty()) {
			return blocks;
		}
		for (Section section : Section.values()) {
			String text = lookup.apply(section.key(farmId));
			List<String> bullets = text == null ? List.of() : bullets(text);
			if (!bullets.isEmpty()) {
				blocks.add(new Block(section, bullets));
			}
		}
		return blocks;
	}

	public static boolean hasInfo(String farmId, Function<String, String> lookup) {
		if (farmId == null || farmId.isEmpty()) {
			return false;
		}
		for (Section section : Section.values()) {
			String text = lookup.apply(section.key(farmId));
			if (text != null && !bullets(text).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Splits a section's text at line feeds, trimming each line and dropping blank ones. */
	static List<String> bullets(String text) {
		List<String> bullets = new ArrayList<>();
		for (String line : text.split("\n")) {
			String bullet = line.strip();
			if (!bullet.isEmpty()) {
				bullets.add(bullet);
			}
		}
		return bullets;
	}

	/** Vanilla loads English first and the chosen language over it, so a key only English has is still found. */
	private static String translation(String key) {
		Language language = Language.getInstance();
		return language.hasTranslation(key) ? language.get(key) : null;
	}
}
