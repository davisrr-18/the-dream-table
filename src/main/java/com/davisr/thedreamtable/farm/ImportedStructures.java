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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds the player's schematic files and lists them under My Schematics, with what to know about each: warnings for the
 * farms, and the reason for files that can't be used, which are listed too so players see why. A file is parsed again
 * only when its modification time or size changes; otherwise the result is reused, since the list is refreshed on every
 * menu click.
 */
public final class ImportedStructures {
	public static final String ID_PREFIX = "nbt:";
	private static final Map<Path, Parsed> CACHE = new HashMap<>();

	private ImportedStructures() {
	}

	/** The imported farms by id, their menu entries in file name order, their warnings by id, and the files that can't be used. */
	public record Imports(Map<String, FarmTemplate> templates, List<FarmOption> options, Map<String, List<ImportNote>> notes, List<Rejected> rejected) {
		static final Imports EMPTY = new Imports(Map.of(), List.of(), Map.of(), List.of());
	}

	/** A file that can't be used, by its name without the extension, and why. */
	public record Rejected(String name, ImportNote reason) {
	}

	private record Parsed(long modified, long size, SchematicReader.Result result) {
	}

	public static FarmTemplate read(String id, InputStream stream) throws IOException {
		return SchematicReader.read(id, stream);
	}

	/** A file name found in more than one folder is loaded once, from the first folder. */
	public static synchronized Imports reload(Path worldRoot) {
		Map<String, FarmTemplate> templates = new LinkedHashMap<>();
		List<FarmOption> options = new ArrayList<>();
		Map<String, List<ImportNote>> notes = new HashMap<>();
		List<Rejected> rejected = new ArrayList<>();
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
		CACHE.keySet().retainAll(files);
		for (Path path : files) {
			load(path, templates, options, notes, rejected);
		}
		return new Imports(Map.copyOf(templates), List.copyOf(options), Map.copyOf(notes), List.copyOf(rejected));
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

	private static void load(Path path, Map<String, FarmTemplate> templates, List<FarmOption> options, Map<String, List<ImportNote>> notes,
			List<Rejected> rejected) {
		String fileName = path.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String display = dot > 0 ? fileName.substring(0, dot) : fileName;
		String id = uniqueId(display, templates);
		SchematicReader.Result result = parsed(path, id, fileName);
		if (result == null) {
			return;
		}
		FarmTemplate template = result.template();
		if (template == null) {
			rejected.add(new Rejected(display, result.notes().isEmpty() ? ImportNote.of(ImportNote.Kind.UNREADABLE) : result.notes().get(0)));
			return;
		}
		if (!result.notes().isEmpty()) {
			notes.put(id, result.notes());
		}
		if (!template.id().equals(id)) {
			template = new FarmTemplate(id, template.steps(), template.materials(), template.center());
		}
		templates.put(id, template);
		options.add(new FarmOption(id, icon(template), "farm.the_dream_table.imported", display));
	}

	/** The result for a file, reusing the last parse while the file is unchanged. Files that can't be used are remembered too. */
	private static SchematicReader.Result parsed(Path path, String id, String fileName) {
		long modified;
		long size;
		try {
			modified = Files.getLastModifiedTime(path).toMillis();
			size = Files.size(path);
		} catch (IOException exception) {
			TheDreamTableMod.LOGGER.warn("Could not read {}", fileName, exception);
			return null;
		}
		Parsed cached = CACHE.get(path);
		if (cached != null && cached.modified() == modified && cached.size() == size) {
			return cached.result();
		}
		SchematicReader.Result result = SchematicReader.read(id, path);
		if (result.template() != null) {
			TheDreamTableMod.LOGGER.info("Imported structure {} as {}", fileName, id);
		} else {
			TheDreamTableMod.LOGGER.warn("Skipped structure {}: {}", fileName, result.notes().isEmpty() ? "unreadable" : result.notes().get(0).kind());
		}
		CACHE.put(path, new Parsed(modified, size, result));
		return result;
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
		String base = ID_PREFIX + display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		if (base.equals(ID_PREFIX)) {
			base = "nbt:structure";
		}
		String id = base;
		int suffix = 2;
		while (templates.containsKey(id) || FarmTemplates.isBuiltIn(id)) {
			id = base + "_" + suffix++;
		}
		return id;
	}
}
