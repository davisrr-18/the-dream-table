package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Finds the player's schematic files and lists them under My Schematics. */
public final class ImportedStructures {
	private ImportedStructures() {
	}

	public static FarmTemplate read(String id, InputStream stream) throws IOException {
		return SchematicReader.read(id, stream);
	}

	/** A file name found in more than one folder is loaded once, from the first folder. */
	public static void reload(Map<String, FarmTemplate> templates, List<FarmOption> options, Path worldRoot) {
		templates.clear();
		options.clear();
		List<Path> files = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (Path folder : folders(worldRoot)) {
			if (!Files.isDirectory(folder)) {
				continue;
			}
			try (Stream<Path> listed = Files.list(folder)) {
				listed.filter(ImportedStructures::isStructureFile).forEach(path -> {
					if (names.add(path.getFileName().toString().toLowerCase(Locale.ROOT))) {
						files.add(path);
					}
				});
			} catch (IOException exception) {
				TheDreamTableMod.LOGGER.warn("Could not list schematics in {}", folder, exception);
			}
		}
		files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
		for (Path path : files) {
			load(path, templates, options);
		}
	}

	private static List<Path> folders(Path worldRoot) {
		Path game = FabricLoader.getInstance().getGameDir();
		List<Path> folders = new ArrayList<>();
		folders.add(game.resolve("schematics"));
		folders.add(game.resolve("config").resolve("worldedit").resolve("schematics"));
		folders.add(game.resolve("config").resolve("litematica"));
		if (worldRoot != null) {
			folders.add(worldRoot.resolve("schematics"));
		}
		folders.add(game.resolve("config").resolve("the_dream_table").resolve("structures"));
		return folders;
	}

	private static void load(Path path, Map<String, FarmTemplate> templates, List<FarmOption> options) {
		String fileName = path.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String display = dot > 0 ? fileName.substring(0, dot) : fileName;
		String id = uniqueId(display, templates);
		try {
			FarmTemplate template = SchematicReader.read(id, path);
			if (template == null) {
				return;
			}
			templates.put(id, template);
			options.add(new FarmOption(id, icon(template), "farm.the_dream_table.imported", display));
			TheDreamTableMod.LOGGER.info("Imported structure {} as {}", display, id);
		} catch (RuntimeException | IOException exception) {
			TheDreamTableMod.LOGGER.warn("Skipped structure {}", fileName, exception);
		}
	}

	private static boolean isStructureFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".nbt") || name.endsWith(".schem") || name.endsWith(".litematic");
	}

	/** The menu icon is the material the structure uses most. */
	private static Item icon(FarmTemplate template) {
		Map<Item, Integer> counts = new HashMap<>();
		for (MaterialSpec spec : template.materials()) {
			counts.merge(spec.item(), spec.count(), Integer::sum);
		}
		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(Items.STRUCTURE_BLOCK);
	}

	private static String uniqueId(String display, Map<String, FarmTemplate> templates) {
		String base = "nbt:" + display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		if (base.equals("nbt:")) {
			base = "nbt:structure";
		}
		String id = base;
		int suffix = 2;
		while (templates.containsKey(id) || FarmTemplates.get(id) != null) {
			id = base + "_" + suffix++;
		}
		return id;
	}
}
