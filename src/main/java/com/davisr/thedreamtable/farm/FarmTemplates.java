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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The built-in farms, read from the bundled schematics, plus the player's imported structures. */
public final class FarmTemplates {
	private static final String STRUCTURES = "/data/the_dream_table/structures/";
	private static final Map<String, FarmTemplate> TEMPLATES = new LinkedHashMap<>();
	/** Replaced as a whole on every reload, so a reader never sees a half-built list. */
	private static volatile ImportedStructures.Imports imports = ImportedStructures.Imports.EMPTY;
	/**
	 * On a client connected to another server, the server's schematics instead of this computer's: their menu entries,
	 * their block counts, and each farm once it arrives. Null while playing on this computer's files.
	 */
	private static volatile List<FarmOption> remoteOptions;
	private static final Map<String, FarmTemplate> REMOTE = new ConcurrentHashMap<>();
	private static final Map<String, Integer> REMOTE_STEPS = new ConcurrentHashMap<>();
	private static volatile Consumer<String> missing = id -> {
	};
	/**
	 * The exact designs players' finished farms were built with, kept in the world, so repairing or taking one down
	 * still works after an update changes or removes that farm. Ids are {@link #savedId}.
	 */
	private static final Map<String, FarmTemplate> SAVED = new ConcurrentHashMap<>();
	private static final String SAVED_PREFIX = "saved:";

	static {
		register(FarmOptions.SUGAR_CANE, "sugar_cane_farm", () -> PlantFarms.pistonRow(FarmOptions.SUGAR_CANE.id(), true));
		register(FarmOptions.BAMBOO, "bamboo_farm", () -> PlantFarms.pistonRow(FarmOptions.BAMBOO.id(), false));
		register(FarmOptions.CROP, "crop_farm", () -> PlantFarms.crop(FarmOptions.CROP.id()));
		register(FarmOptions.MOB_DARK_ROOM, "mob_farm", () -> MobFarms.darkRoom(FarmOptions.MOB_DARK_ROOM.id()));
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
		if (builtIn != null) {
			return builtIn;
		}
		FarmTemplate saved = SAVED.get(id);
		if (saved != null) {
			return saved;
		}
		if (remoteOptions == null) {
			return imports.templates().get(id);
		}
		FarmTemplate remote = REMOTE.get(id);
		if (remote == null && (isImported(id) || isSaved(id))) {
			missing.accept(id);
		}
		return remote;
	}

	/** The id a finished farm's own design is kept under: its farm and the signature of the design it was built with. */
	public static String savedId(String farmId, long signature) {
		return SAVED_PREFIX + baseId(farmId) + "#" + Long.toHexString(signature);
	}

	public static boolean isSaved(String id) {
		return id.startsWith(SAVED_PREFIX);
	}

	/** The farm a saved design belongs to, for its name and icon; other ids are returned as they are. */
	public static String baseId(String id) {
		if (!isSaved(id)) {
			return id;
		}
		int mark = id.lastIndexOf('#');
		return id.substring(SAVED_PREFIX.length(), mark < 0 ? id.length() : mark);
	}

	/** Keeps a finished farm's design under its saved id. The template's own id must already be that id. */
	public static void keepSaved(FarmTemplate template) {
		SAVED.putIfAbsent(template.id(), template);
	}

	/** Imported schematics have ids made by {@link ImportedStructures}; anything else is a built-in farm. */
	public static boolean isImported(String id) {
		return id.startsWith(ImportedStructures.ID_PREFIX);
	}

	/** The farm's block count, also known for a server's schematic that hasn't arrived yet; -1 when unknown. */
	public static int stepCount(String id) {
		FarmTemplate template = TEMPLATES.containsKey(id) || remoteOptions == null ? get(id) : REMOTE.get(id);
		return template != null ? template.steps().size() : REMOTE_STEPS.getOrDefault(id, -1);
	}

	/** Switches this client to the server's schematic list. Farms that changed are fetched again when next needed. */
	public static void useRemote(List<FarmOption> options, Map<String, Integer> steps) {
		REMOTE_STEPS.clear();
		REMOTE_STEPS.putAll(steps);
		REMOTE.keySet().removeIf(id -> isImported(id) && !steps.containsKey(id));
		remoteOptions = List.copyOf(options);
	}

	public static void useLocal() {
		remoteOptions = null;
		REMOTE.clear();
		REMOTE_STEPS.clear();
	}

	public static void putRemote(FarmTemplate template) {
		if (remoteOptions != null) {
			REMOTE.put(template.id(), template);
		}
	}

	/** Called on the client with an imported farm it doesn't have yet, so it can ask the server for it. */
	public static void onMissing(Consumer<String> listener) {
		missing = listener;
	}

	public static boolean isBuiltIn(String id) {
		return TEMPLATES.containsKey(id);
	}

	public static List<FarmOption> options() {
		List<FarmOption> options = new ArrayList<>(FarmOptions.ALL);
		options.addAll(imported());
		return options;
	}

	public static List<FarmOption> imported() {
		List<FarmOption> remote = remoteOptions;
		return remote != null ? remote : imports.options();
	}

	/** Uses the bundled schematic, or the code-built farm when the file is missing or unreadable. */
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
		TEMPLATES.put(template.id(), template);
	}
}
