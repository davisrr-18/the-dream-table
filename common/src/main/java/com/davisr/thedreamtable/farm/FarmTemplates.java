package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.legacy.MobFarms;
import com.davisr.thedreamtable.farm.legacy.PlantFarms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
	private static volatile Map<String, List<ImportNote>> remoteNotes = Map.of();
	private static volatile List<ImportedStructures.Rejected> remoteRejected = List.of();
	private static final Map<String, FarmTemplate> REMOTE = new ConcurrentHashMap<>();
	private static final Map<String, Integer> REMOTE_STEPS = new ConcurrentHashMap<>();
	/** The file version the server listed for each imported farm, to tell a farm held in {@link #REMOTE} from an edited file. */
	private static final Map<String, Long> REMOTE_VERSIONS = new ConcurrentHashMap<>();
	/** The last reply version dropped per farm for not matching the list; the same one again means the list is the stale side. */
	private static final Map<String, Long> DROPPED_VERSIONS = new ConcurrentHashMap<>();
	private static volatile Consumer<String> missing = id -> {
	};
	/**
	 * The exact designs players' finished farms were built with, kept in the world, so repairing or taking one down
	 * still works after an update changes or removes that farm. Ids are {@link #savedId}.
	 */
	private static final Map<String, FarmTemplate> SAVED = new ConcurrentHashMap<>();
	private static final String SAVED_PREFIX = "saved:";
	private static final long RELOAD_MS = 1000L;
	private static Path reloadedRoot;
	private static long reloadedAt;
	private static final ImportRefresher REFRESHER = new ImportRefresher(ImportedStructures::reload);
	private static volatile Consumer<MinecraftServer> importsChanged = server -> {
	};

	static {
		register(FarmOptions.SUGAR_CANE, "sugar_cane_farm", () -> PlantFarms.pistonRow(FarmOptions.SUGAR_CANE.id(), true));
		register(FarmOptions.BAMBOO, "bamboo_farm", () -> PlantFarms.pistonRow(FarmOptions.BAMBOO.id(), false));
		register(FarmOptions.CACTUS, "cactus_farm");
		register(FarmOptions.CROP, "crop_farm", () -> PlantFarms.crop(FarmOptions.CROP.id()));
		register(FarmOptions.HONEY, "honey_farm");
		register(FarmOptions.MOB_DARK_ROOM, "mob_farm", () -> MobFarms.darkRoom(FarmOptions.MOB_DARK_ROOM.id()));
		register(FarmOptions.CREEPER, "creeper_farm");
		register(FarmOptions.IRON_GOLEM, "iron_farm", () -> MobFarms.iron(FarmOptions.IRON_GOLEM.id()));
	}

	private FarmTemplates() {
	}

	public static void load() {
		reloadImports(null);
	}

	/**
	 * Reads the schematic folders now, on the calling thread, for startup only. Table clicks use
	 * {@link #refreshImports}, which never waits for files. For the same world this happens at most once per
	 * {@link #RELOAD_MS}.
	 */
	public static synchronized void reloadImports(Path worldRoot) {
		long now = System.currentTimeMillis();
		if (Objects.equals(worldRoot, reloadedRoot) && now - reloadedAt < RELOAD_MS) {
			return;
		}
		reloadedRoot = worldRoot;
		reloadedAt = now;
		imports = ImportedStructures.reload(worldRoot);
	}

	/**
	 * Asks for the schematic folders to be read again on a worker thread, at most once per {@link #RELOAD_MS} for a world
	 * and never while a read runs. Callers keep using the last published list; a result that differs is swapped in on the
	 * server thread and {@link #onImportsChanged} is told, so a table click never waits for files to be parsed.
	 */
	public static void refreshImports(MinecraftServer server) {
		Path root = server.getSavePath(WorldSavePath.ROOT);
		long now = System.currentTimeMillis();
		synchronized (FarmTemplates.class) {
			if (Objects.equals(root, reloadedRoot) && now - reloadedAt < RELOAD_MS) {
				return;
			}
			if (!REFRESHER.request(root, server::execute, result -> publish(server, root, result), failure -> {
			})) {
				return;
			}
			reloadedRoot = root;
			reloadedAt = now;
		}
	}

	/**
	 * Reads the schematic folders on the worker thread whatever the throttle says, for {@code /dreamtable reload}, and calls
	 * {@code done} on the server thread with the published list, or {@code failed} when the read threw. Files that did not
	 * change are not parsed again.
	 *
	 * @return false when a read is already running; neither callback is called then
	 */
	public static boolean reloadImportsAsync(MinecraftServer server, Consumer<ImportedStructures.Imports> done, Consumer<Exception> failed) {
		Path root = server.getSavePath(WorldSavePath.ROOT);
		boolean started = REFRESHER.request(root, server::execute, result -> {
			publish(server, root, result);
			done.accept(result);
		}, failed);
		if (started) {
			synchronized (FarmTemplates.class) {
				reloadedRoot = root;
				reloadedAt = System.currentTimeMillis();
			}
		}
		return started;
	}

	/** Swaps in a finished read on the server thread, unless the server it was read for has gone. */
	private static void publish(MinecraftServer server, Path root, ImportedStructures.Imports result) {
		if (!server.isRunning()) {
			return;
		}
		synchronized (FarmTemplates.class) {
			if (!Objects.equals(root, reloadedRoot)) {
				return;
			}
		}
		ImportedStructures.Imports previous = imports;
		imports = result;
		if (!previous.sameListing(result)) {
			importsChanged.accept(server);
		}
	}

	/** The last published list of imported schematics, for counts. */
	public static ImportedStructures.Imports importsSnapshot() {
		return imports;
	}

	/** Called on the server thread after a finished read changed the listing, so players with a table open can be sent the new one. */
	public static void onImportsChanged(Consumer<MinecraftServer> listener) {
		importsChanged = listener;
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

	/** A globally unique id for one persisted layout, including designs whose legacy signature is the same. */
	public static String newSavedId(String farmId, long signature) {
		return savedId(farmId, signature) + "@" + UUID.randomUUID();
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

	/** Forgets the designs of the world that just closed; the next server reads its own back as its farms load. */
	public static void clearSaved() {
		SAVED.clear();
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

	/** The version of an imported farm's file on this computer, sent to clients so they notice an edited file. */
	public static long version(String id) {
		return imports.versions().getOrDefault(id, 0L);
	}

	public static void useRemote(List<FarmOption> options, Map<String, Integer> steps, Map<String, List<ImportNote>> notes,
			List<ImportedStructures.Rejected> rejected) {
		useRemote(options, steps, Map.of(), notes, rejected);
	}

	/**
	 * Switches this client to the server's schematic list. A farm held from before is kept only while the server's file
	 * has the same version, so an edited schematic is fetched again when next needed.
	 */
	public static void useRemote(List<FarmOption> options, Map<String, Integer> steps, Map<String, Long> versions,
			Map<String, List<ImportNote>> notes, List<ImportedStructures.Rejected> rejected) {
		REMOTE_STEPS.clear();
		REMOTE_STEPS.putAll(steps);
		REMOTE.keySet().removeIf(id -> isImported(id) && !(versions.containsKey(id) && Objects.equals(versions.get(id), REMOTE_VERSIONS.get(id))));
		REMOTE_VERSIONS.clear();
		REMOTE_VERSIONS.putAll(versions);
		DROPPED_VERSIONS.clear();
		remoteNotes = Map.copyOf(notes);
		remoteRejected = List.copyOf(rejected);
		remoteOptions = List.copyOf(options);
	}

	public static void useLocal() {
		remoteOptions = null;
		remoteNotes = Map.of();
		remoteRejected = List.of();
		REMOTE.clear();
		REMOTE_STEPS.clear();
		REMOTE_VERSIONS.clear();
		DROPPED_VERSIONS.clear();
	}

	public static void putRemote(FarmTemplate template) {
		if (remoteOptions != null) {
			REMOTE.put(template.id(), template);
		}
	}

	/**
	 * Stores a farm the server sent together with the file version it was read at. A reply for a different version than
	 * the list announced (the file was edited while the request was on its way) is dropped, not stored as current; it is
	 * asked for again. If the answer to that comes back at the same different version, the file changed after the list was
	 * sent, so the reply is the current one and becomes the listed version. Farms the list doesn't version, like a table's
	 * saved design, are always kept.
	 *
	 * @return true when the farm was stored
	 */
	public static boolean putRemote(FarmTemplate template, long version) {
		String id = template.id();
		Long listed = REMOTE_VERSIONS.get(id);
		if (listed != null && listed != version) {
			if (!Objects.equals(DROPPED_VERSIONS.put(id, version), version)) {
				return false;
			}
			REMOTE_VERSIONS.put(id, version);
		}
		DROPPED_VERSIONS.remove(id);
		putRemote(template);
		return remoteOptions != null;
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

	/** What to know before building an imported farm, like blocks left out or only available in Creative. */
	public static List<ImportNote> notes(String id) {
		return (remoteOptions != null ? remoteNotes : imports.notes()).getOrDefault(id, List.of());
	}

	/** Schematic files that can't be used, with the reason, for My Schematics. */
	public static List<ImportedStructures.Rejected> rejected() {
		return remoteOptions != null ? remoteRejected : imports.rejected();
	}

	/** Uses the bundled schematic, or the code-built farm when the file is missing or unreadable. */
	private static void register(FarmOption option, String file, Supplier<FarmTemplate> fallback) {
		FarmTemplate template = readBundled(option, file);
		if (template == null) {
			template = fallback.get();
		}
		TEMPLATES.put(template.id(), template);
	}

	/** Registers a farm whose supplied schematic is its only design. */
	private static void register(FarmOption option, String file) {
		FarmTemplate template = readBundled(option, file);
		if (template != null) {
			TEMPLATES.put(template.id(), template);
		}
	}

	private static FarmTemplate readBundled(FarmOption option, String file) {
		FarmTemplate template = null;
		try (InputStream stream = FarmTemplates.class.getResourceAsStream(STRUCTURES + file + ".schem")) {
			if (stream != null) {
				template = ImportedStructures.read(option.id(), stream);
			}
		} catch (IOException | RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Could not load the {} schematic", file, exception);
		}
		return template;
	}
}
