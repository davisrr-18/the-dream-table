package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.legacy.MobFarms;
import com.davisr.thedreamtable.farm.legacy.PlantFarms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The built-in farms, read from the bundled schematics, plus the player's imported structures. */
public final class FarmTemplates {
	private static final String STRUCTURES = "/data/the_dream_table/structures/";
	private static final Map<String, FarmTemplate> TEMPLATES = new LinkedHashMap<>();
	/** Replaced as a whole on every reload, so a reader never sees a half-built list. */
	private static volatile ImportedStructures.Imports imports = ImportedStructures.Imports.EMPTY;

	static {
		register(FarmOptions.SUGAR_CANE, "sugar_cane_farm", () -> PlantFarms.pistonRow(FarmOptions.SUGAR_CANE.id(), true));
		register(FarmOptions.SUGAR_CANE_MEDIUM, "sugar_cane_farm_medium", () -> null);
		register(FarmOptions.SUGAR_CANE_BIG, "sugar_cane_farm_big", () -> null);
		register(FarmOptions.BAMBOO, "bamboo_farm", () -> PlantFarms.pistonRow(FarmOptions.BAMBOO.id(), false));
		register(FarmOptions.BAMBOO_MEDIUM, "bamboo_farm_medium", () -> null);
		register(FarmOptions.BAMBOO_BIG, "bamboo_farm_big", () -> null);
		register(FarmOptions.CROP_TINY, "crop_farm_tiny", () -> null);
		register(FarmOptions.CROP, "crop_farm", () -> PlantFarms.crop(FarmOptions.CROP.id()));
		register(FarmOptions.MOB_DARK_ROOM, "mob_farm", () -> MobFarms.darkRoom(FarmOptions.MOB_DARK_ROOM.id()));
		register(FarmOptions.MOB_DARK_ROOM_BIG, "mob_farm_big", () -> null);
		register(FarmOptions.IRON_GOLEM, "iron_farm", () -> MobFarms.iron(FarmOptions.IRON_GOLEM.id()));
	}

	private FarmTemplates() {
	}

	public static void load() {
		reloadImports(null);
	}

	public static void reloadImports(Path worldRoot) {
		imports = ImportedStructures.reload(worldRoot);
	}

	public static FarmTemplate get(String id) {
		FarmTemplate builtIn = TEMPLATES.get(id);
		return builtIn != null ? builtIn : imports.templates().get(id);
	}

	public static boolean isBuiltIn(String id) {
		return TEMPLATES.containsKey(id);
	}

	public static List<FarmOption> options() {
		List<FarmOption> options = new ArrayList<>(FarmOptions.ALL);
		options.addAll(imports.options());
		return options;
	}

	public static List<FarmOption> imported() {
		return imports.options();
	}

	/**
	 * Uses the bundled schematic, or the code-built farm when the file is missing or unreadable.
	 * Medium and Big sizes come from {@code tools/farmgen.py} and have no code-built fallback.
	 */
	private static void register(FarmOption option, String file, Supplier<FarmTemplate> fallback) {
		FarmTemplate template = null;
		try (InputStream stream = FarmTemplates.class.getResourceAsStream(STRUCTURES + file + ".schem")) {
			if (stream != null) {
				template = ImportedStructures.read(option.id(), stream);
			}
		} catch (IOException | RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Could not load the {} schematic", file, exception);
		}
		if (template == null) {
			template = fallback.get();
		}
		if (template != null) {
			TEMPLATES.put(template.id(), template);
		}
	}
}
