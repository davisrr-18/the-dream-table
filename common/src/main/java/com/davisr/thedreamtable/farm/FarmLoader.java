package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.chunk.WorldChunk;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the finished farms each player picked in My Farms loaded, up to the server's limit, and tells the owner once
 * when a loaded farm's chests and barrels are all full.
 * Every chunk a farm covers gets a ticket that makes it entity-ticking, so plants grow, items fall and hoppers move them.
 * Tickets are renewed every few seconds and expire on their own, so an unpicked or taken-down farm unloads soon.
 * The game gives random ticks, which make plants grow, only to chunks within 128 blocks of a player, and stops
 * entities in a dimension nobody is in; for picked farms the loader gives those random ticks itself, at the world's
 * own rate and only where the game doesn't, and keeps their dimension awake. Natural mob spawning still needs a player
 * within 128 blocks no matter what, so mob farms only run with someone nearby.
 */
public final class FarmLoader extends PersistentState {
	static final String STATE_KEY = "the_dream_table_loaded_farms";
	private static final Type<FarmLoader> TYPE = new Type<>(FarmLoader::new, FarmLoader::read, null);
	private static final int INTERVAL_TICKS = 100;
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("the_dream_table_farm", Comparator.comparingLong(ChunkPos::toLong), INTERVAL_TICKS * 3);
	/** Radius 2 makes the farm's chunk entity-ticking, which item drops and golems need. */
	private static final int RADIUS = 2;
	/** Blocks from a player within which the game gives random ticks by itself. */
	private static final double RANDOM_TICK_RANGE = 128.0;
	/**
	 * Most chunks one farm may cover and still be kept loaded. Each covered chunk gets its own radius-2 ticket and its own
	 * random ticks, and a farm's footprint is bounded only by its block count, so a sparse imported schematic could otherwise
	 * span a huge area. The biggest built-in farm covers 25 chunks at worst (the iron farm, 5 by 5); 64 is an 8 by 8 square,
	 * 128 blocks wide, leaving room for large imported farms.
	 */
	public static final int MAX_CHUNKS_PER_FARM = 64;
	/** Set when a farm was unpicked or taken down, so the next tick renews at once instead of up to 100 ticks later. */
	private static volatile boolean renewalRequested;

	/** Per player, the picked farms as "dimension|farm id within that world". */
	private final Map<UUID, Set<String>> picked = new HashMap<>();

	/**
	 * One of a player's finished farms, as listed in My Farms, with its {@link FarmStorage} status, how many of its
	 * blocks are missing, and how full its chests and barrels are in percent. Missing and storage are {@link #UNKNOWN}
	 * when part of the farm is not loaded; storage also when the farm has none.
	 */
	public record Entry(String key, String farmId, String layoutId, String customName, String dimension, BlockPos where,
			boolean favorite, boolean tracked, boolean loaded, boolean ownerQuotaFull, int status, int missing, boolean available, int storage,
			int alerts) {
		/** An entry with no active alert. */
		public Entry(String key, String farmId, String layoutId, String customName, String dimension, BlockPos where,
				boolean favorite, boolean tracked, boolean loaded, boolean ownerQuotaFull, int status, int missing, boolean available, int storage) {
			this(key, farmId, layoutId, customName, dimension, where, favorite, tracked, loaded, ownerQuotaFull, status, missing, available, storage, 0);
		}

		/** The exact saved design when available, otherwise the base id for older list data and display fallbacks. */
		public String designId() {
			return layoutId.isEmpty() ? farmId : layoutId;
		}

		/** Custom names are already validated server-side; the design id is the client fallback. */
		public String displayName() {
			return customName.isEmpty() ? farmId : customName;
		}
	}

	public static final int UNKNOWN = -1;
	public enum ToggleResult {
		SELECTED, UNSELECTED, OWNER_QUOTA_FULL, TOO_LARGE, NOT_FOUND, FORBIDDEN
	}

	/** A finished farm and the world it stands in. */
	public record Located(String key, ServerWorld world, FarmAreas.Finished farm) {
	}

	/** Farms already reported full, so the owner hears about it once, until it has room again. */
	private final Set<String> reportedFull = new HashSet<>();

	/** Farms already logged as too large to keep loaded, so the same farm isn't logged at every renewal. */
	private final Set<String> reportedTooLarge = new HashSet<>();

	/** The chunks of the picked farms, per world, as of the last renewal; only these get the loader's random ticks. Not saved. */
	private final Map<ServerWorld, LongSet> loadedChunks = new HashMap<>();

	/** How many farms the last renewal kept loaded. Not saved. */
	private int keptFarms;

	/** An error was already logged, so the same failure isn't logged every tick. */
	private boolean failing;

	/** What the loader keeps, for {@code /dreamtable status}: farms players picked, farms kept loaded at the last renewal, and their chunks. */
	public record Status(int picked, int keptFarms, int keptChunks) {
	}

	public Status status() {
		int picks = picked.values().stream().mapToInt(Set::size).sum();
		int chunks = loadedChunks.values().stream().mapToInt(LongSet::size).sum();
		return new Status(picks, keptFarms, chunks);
	}

	public static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			FarmLoader loader = of(server);
			try {
				if (renewalDue(server.getTicks(), consumeRenewalRequest())) {
					loader.keepLoaded(server);
				}
				loader.tickFarms();
				loader.failing = false;
			} catch (RuntimeException exception) {
				if (!loader.failing) {
					loader.failing = true;
					TheDreamTableMod.LOGGER.error("Keeping farms loaded failed; the random ticks are retried every tick, the renewal at the next interval, and it is logged once until it works", exception);
				}
			}
		});
	}

	/** Whether the tickets and the ticked chunks are renewed this tick: every interval, or sooner when a change asked for it. */
	static boolean renewalDue(long serverTicks, boolean requested) {
		return requested || serverTicks % INTERVAL_TICKS == 0;
	}

	/** Asks for a renewal on the next tick, so a farm that stopped being kept loaded stops getting random ticks at once. */
	public static void requestRenewal() {
		renewalRequested = true;
	}

	static boolean consumeRenewalRequest() {
		boolean requested = renewalRequested;
		renewalRequested = false;
		return requested;
	}

	/** How many chunks the box covers, counting every chunk it touches. */
	static long chunkCount(Box box) {
		long chunksX = (long) (((int) Math.floor(box.maxX - 1)) >> 4) - ((int) Math.floor(box.minX) >> 4) + 1;
		long chunksZ = (long) (((int) Math.floor(box.maxZ - 1)) >> 4) - ((int) Math.floor(box.minZ) >> 4) + 1;
		return chunksX * chunksZ;
	}

	/** Whether a farm covering this box is small enough to keep loaded. */
	static boolean withinChunkCap(Box box) {
		return chunkCount(box) <= MAX_CHUNKS_PER_FARM;
	}

	private void tickFarms() {
		for (Map.Entry<ServerWorld, LongSet> entry : loadedChunks.entrySet()) {
			ServerWorld world = entry.getKey();
			world.resetIdleTimeout();
			int speed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
			if (speed <= 0) {
				continue;
			}
			LongIterator chunks = entry.getValue().iterator();
			while (chunks.hasNext()) {
				ChunkPos pos = new ChunkPos(chunks.nextLong());
				WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
				if (chunk != null && !nearPlayer(world, pos)) {
					world.tickChunk(chunk, speed);
				}
			}
		}
	}

	/** The same test the game uses before giving a chunk random ticks, so a chunk is never ticked twice. */
	private static boolean nearPlayer(ServerWorld world, ChunkPos pos) {
		double x = pos.getCenterX();
		double z = pos.getCenterZ();
		for (ServerPlayerEntity player : world.getPlayers()) {
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			if (!player.isSpectator() && dx * dx + dz * dz < RANDOM_TICK_RANGE * RANDOM_TICK_RANGE) {
				return true;
			}
		}
		return false;
	}

	public static FarmLoader of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
	}

	/** Every finished farm of the player, newest first, and whether each is picked. */
	public List<Entry> farmsOf(MinecraftServer server, UUID player) {
		FarmPreferences preferences = FarmPreferences.of(server);
		List<Entry> entries = new ArrayList<>();
		Map<String, Long> finishedAt = new HashMap<>();
		for (FarmDirectory.Located located : FarmDirectory.ownedBy(server, player)) {
			FarmAreas.Finished finished = located.farm();
			String key = located.key().externalForm();
			FarmTemplate layout = finished.layout();
			int status = FarmStorage.status(located.world(), layout, finished.site());
			Set<String> ownerSelections = picked.getOrDefault(finished.ownerUuid(), Set.of());
			boolean loaded = ownerSelections.contains(key);
			Located farm = new Located(key, located.world(), finished);
			finishedAt.put(key, finished.finishedAt());
			entries.add(new Entry(key, finished.farmId(), layout == null ? "" : layout.id(), finished.customName() == null ? "" : finished.customName(),
					located.key().dimension().toString(), finished.where(), preferences.isFavorite(player, located.key()),
					preferences.tracked(player).filter(located.key()::equals).isPresent(), loaded,
					!loaded && ownerSelections.size() >= DreamTableConfig.maxLoadedFarms(), status, missing(farm, layout), layout != null,
					storagePercent(farm, layout, status),
					FarmAreas.of(located.world()).alerts(finished.instanceId()).map(alerts -> alerts.active().size()).orElse(0)));
		}
		entries.sort(Comparator.comparingLong((Entry entry) -> finishedAt.getOrDefault(entry.key(), 0L)).reversed());
		return entries;
	}

	private static int storagePercent(Located farm, FarmTemplate layout, int status) {
		if (layout == null || status == FarmStorage.UNLOADED) {
			return UNKNOWN;
		}
		FarmStorage.Contents contents = FarmStorage.contents(farm.world(), layout, farm.farm().site());
		return contents == null || contents.capacity() == 0 ? UNKNOWN : contents.stored() * 100 / contents.capacity();
	}

	/** How many farms the player picked, never above the server's current limit. */
	public int loadedCount(UUID player) {
		return Math.min(picked.getOrDefault(player, Set.of()).size(), DreamTableConfig.maxLoadedFarms());
	}

	/** Picks or unpicks an existing farm. Client-provided keys are resolved again on the server. */
	public ToggleResult toggle(MinecraftServer server, UUID actorUuid, FarmKey key) {
		FarmDirectory.Located located = FarmDirectory.resolve(server, key).orElse(null);
		if (located == null) {
			return ToggleResult.NOT_FOUND;
		}
		if (!FarmAccess.allows(located.record(), actorUuid, FarmAction.KEEP_LOADED)) {
			return ToggleResult.FORBIDDEN;
		}
		UUID owner = located.farm().ownerUuid();
		String externalKey = key.externalForm();
		if (!picked.getOrDefault(owner, Set.of()).contains(externalKey) && !withinChunkCap(located.farm().box())) {
			return ToggleResult.TOO_LARGE;
		}
		return toggleKnown(owner, externalKey, true);
	}

	ToggleResult toggleKnown(UUID ownerUuid, String key, boolean known) {
		Set<String> keys = picked.get(ownerUuid);
		if (keys != null && keys.remove(key)) {
			if (keys.isEmpty()) {
				picked.remove(ownerUuid);
			}
			markDirty();
			requestRenewal();
			return ToggleResult.UNSELECTED;
		}
		if (!known) {
			return ToggleResult.NOT_FOUND;
		}
		keys = picked.computeIfAbsent(ownerUuid, ignored -> new LinkedHashSet<>());
		if (keys.size() >= DreamTableConfig.maxLoadedFarms()) {
			if (keys.isEmpty()) {
				picked.remove(ownerUuid);
			}
			return ToggleResult.OWNER_QUOTA_FULL;
		}
		keys.add(key);
		markDirty();
		return ToggleResult.SELECTED;
	}

	private void keepLoaded(MinecraftServer server) {
		loadedChunks.clear();
		keptFarms = 0;
		server.getWorlds().forEach(world -> FarmAreas.of(world).reconcileRepairs(world));
		int limit = DreamTableConfig.maxLoadedFarms();
		for (Map.Entry<UUID, Set<String>> entry : picked.entrySet()) {
			Set<String> keys = entry.getValue();
			if (keys.isEmpty() || limit <= 0) {
				continue;
			}
			Set<String> existing = new LinkedHashSet<>();
			int loaded = 0;
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
			for (Located farm : located(server, entry.getKey())) {
				if (keys.contains(farm.key())) {
					existing.add(farm.key());
				}
				if (keys.contains(farm.key()) && !withinChunkCap(farm.farm().box())) {
					reportTooLarge(farm);
				} else if (keys.contains(farm.key()) && loaded < limit && (DreamTableConfig.loadWhileOffline() || ownerOnline(server, farm.farm()))) {
					loaded++;
					keptFarms++;
					load(farm.world(), farm.farm().box(), loadedChunks.computeIfAbsent(farm.world(), ignored -> new LongOpenHashSet()));
					reportIfFull(owner, farm);
				}
			}
			if (pruneMissing(keys, existing)) {
				markDirty();
			}
		}
	}

	/** A farm picked before the cap, or grown past it, is skipped; logged once so the server owner can see why it isn't kept loaded. */
	private void reportTooLarge(Located farm) {
		if (reportedTooLarge.add(farm.key())) {
			TheDreamTableMod.LOGGER.warn("Farm {} covers {} chunks, over the Keep Loaded limit of {}, so it is not kept loaded",
					farm.key(), chunkCount(farm.farm().box()), MAX_CHUNKS_PER_FARM);
		}
	}

	/** A farm is kept loaded while its owner is online, unless the server keeps it loaded while the owner is away. */
	private static boolean ownerOnline(MinecraftServer server, FarmAreas.Finished farm) {
		return server.getPlayerManager().getPlayer(farm.ownerUuid()) != null;
	}

	/** Blocks a repair would place, by the farm's own saved design, or {@link #UNKNOWN} while part of it is unloaded. */
	public static int missing(Located farm, FarmTemplate template) {
		if (template == null) {
			return UNKNOWN;
		}
		FarmAreas.Presence presence = FarmAreas.presence(farm.world(), template, farm.farm().site(), farm.farm().swaps());
		return presence.unloaded() ? UNKNOWN : presence.missing();
	}

	private void reportIfFull(ServerPlayerEntity owner, Located farm) {
		boolean full = FarmStorage.status(farm.world(), farm.farm().layout(), farm.farm().site()) == FarmStorage.FULL;
		if (!full) {
			reportedFull.remove(farm.key());
			return;
		}
		if (owner != null && reportedFull.add(farm.key())) {
			BlockPos where = farm.farm().where();
			String customName = farm.farm().customName();
			Text name = customName != null && !customName.isBlank() ? Text.literal(customName)
					: FarmOptions.byId(farm.farm().farmId()).map(FarmOptions.FarmOption::title).orElse(Text.literal(farm.farm().farmId()));
			owner.sendMessage(Text.translatable("gui.the_dream_table.storage_full", name, where.getX(), where.getY(), where.getZ()), false);
		}
	}

	/** The player's finished farm with this My Farms key. */
	public Optional<Located> byKey(MinecraftServer server, UUID player, String key) {
		return findByKey(located(server, player), key);
	}

	/** Selects a listed farm by its stable world key, independent of the list's current order. */
	static Optional<Located> findByKey(List<Located> farms, String key) {
		return farms.stream().filter(farm -> farm.key().equals(key)).findFirst();
	}

	private static List<Located> located(MinecraftServer server, UUID player) {
		List<Located> farms = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			for (FarmAreas.Finished farm : FarmAreas.of(world).finishedBy(player)) {
				farms.add(new Located(FarmKey.of(world, farm.instanceId()).externalForm(), world, farm));
			}
		}
		farms.sort(Comparator.comparingLong((Located farm) -> farm.farm().finishedAt()).reversed());
		return farms;
	}

	/** Replaces positional pre-2.2 selections after every dimension has loaded. */
	public void migrateLegacyKeys(MinecraftServer server) {
		boolean changed = false;
		for (Map.Entry<UUID, Set<String>> entry : picked.entrySet()) {
			Map<String, String> replacements = new HashMap<>();
			for (Located farm : located(server, entry.getKey())) {
				String dimension = farm.world().getRegistryKey().getValue().toString();
				replacements.put(dimension + "|" + farm.farm().id(), farm.key());
			}
			changed |= migrateKeys(entry.getValue(), replacements);
		}
		if (changed) {
			markDirty();
		}
	}

	static boolean migrateKeys(Set<String> picked, Map<String, String> replacements) {
		Set<String> migrated = new LinkedHashSet<>();
		for (String key : picked) {
			try {
				FarmKey.parse(key);
				migrated.add(key);
			} catch (IllegalArgumentException legacy) {
				String replacement = replacements.get(key);
				if (replacement != null) {
					migrated.add(replacement);
				}
			}
		}
		if (picked.equals(migrated)) {
			return false;
		}
		picked.clear();
		picked.addAll(migrated);
		return true;
	}

	static boolean pruneMissing(Set<String> picked, Set<String> existing) {
		return picked.retainAll(existing);
	}

	private static void load(ServerWorld world, Box box, LongSet chunks) {
		int minX = (int) Math.floor(box.minX) >> 4;
		int maxX = (int) Math.floor(box.maxX - 1) >> 4;
		int minZ = (int) Math.floor(box.minZ) >> 4;
		int maxZ = (int) Math.floor(box.maxZ - 1) >> 4;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				ChunkPos chunk = new ChunkPos(x, z);
				world.getChunkManager().addTicket(TICKET, chunk, RADIUS, chunk);
				chunks.add(chunk.toLong());
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound players = new NbtCompound();
		for (Map.Entry<UUID, Set<String>> entry : picked.entrySet()) {
			NbtList keys = new NbtList();
			for (String key : entry.getValue()) {
				keys.add(NbtString.of(key));
			}
			players.put(entry.getKey().toString(), keys);
		}
		nbt.put("Players", players);
		return nbt;
	}

	/** Reads at most the server's hard limit of picked farms per player, so a corrupt list stays bounded. */
	static FarmLoader read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		FarmLoader loader = new FarmLoader();
		NbtCompound players = nbt.getCompound("Players");
		for (String player : players.getKeys()) {
			UUID id;
			try {
				id = UUID.fromString(player);
			} catch (IllegalArgumentException malformed) {
				continue;
			}
			Set<String> keys = new LinkedHashSet<>();
			NbtList list = players.getList(player, NbtElement.STRING_TYPE);
			for (int index = 0; index < Math.min(list.size(), DreamTableConfig.MAX_LOADED_LIMIT); index++) {
				keys.add(list.getString(index));
			}
			loader.picked.put(id, keys);
		}
		return loader;
	}
}
