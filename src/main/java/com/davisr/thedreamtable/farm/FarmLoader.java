package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
	private static final Type<FarmLoader> TYPE = new Type<>(FarmLoader::new, FarmLoader::read, null);
	private static final int INTERVAL_TICKS = 100;
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("the_dream_table_farm", Comparator.comparingLong(ChunkPos::toLong), INTERVAL_TICKS * 3);
	/** Radius 2 makes the farm's chunk entity-ticking, which item drops and golems need. */
	private static final int RADIUS = 2;
	/** Blocks from a player within which the game gives random ticks by itself. */
	private static final double RANDOM_TICK_RANGE = 128.0;

	/** Per player, the picked farms as "dimension|farm id within that world". */
	private final Map<UUID, Set<String>> picked = new HashMap<>();

	/**
	 * One of a player's finished farms, as listed in My Farms, with its {@link FarmStorage} status and how many of its
	 * blocks are missing, or {@link #UNKNOWN} when part of it is not loaded.
	 */
	public record Entry(String key, String farmId, String dimension, BlockPos where, boolean loaded, int status, int missing, boolean available) {
	}

	public static final int UNKNOWN = -1;

	/** A finished farm and the world it stands in. */
	public record Located(String key, ServerWorld world, FarmAreas.Finished farm) {
	}

	/** Farms already reported full, so the owner hears about it once, until it has room again. */
	private final Set<String> reportedFull = new HashSet<>();

	/** The chunks of the picked farms, per world, as of the last renewal. Not saved. */
	private final Map<ServerWorld, LongSet> loadedChunks = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			FarmLoader loader = of(server);
			if (server.getTicks() % INTERVAL_TICKS == 0) {
				loader.keepLoaded(server);
			}
			loader.tickFarms();
		});
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
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "the_dream_table_loaded_farms");
	}

	/** Every finished farm of the player, newest first, and whether each is picked. */
	public List<Entry> farmsOf(MinecraftServer server, UUID player) {
		Set<String> keys = picked.getOrDefault(player, Set.of());
		List<Entry> entries = new ArrayList<>();
		for (Located farm : located(server, player)) {
			String dimension = farm.world().getRegistryKey().getValue().toString();
			FarmTemplate layout = farm.farm().layout();
			int status = FarmStorage.status(farm.world(), layout, farm.farm().site());
			entries.add(new Entry(farm.key(), farm.farm().farmId(), dimension, farm.farm().where(), keys.contains(farm.key()), status, missing(farm, layout),
					layout != null));
		}
		return entries;
	}

	/** How many farms the player picked, never above the server's current limit. */
	public int loadedCount(UUID player) {
		return Math.min(picked.getOrDefault(player, Set.of()).size(), DreamTableConfig.maxLoadedFarms());
	}

	/** Picks or unpicks a farm. Returns false when picking it would go over the server's limit. */
	public boolean toggle(UUID player, String key) {
		Set<String> keys = picked.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
		if (keys.remove(key)) {
			markDirty();
			return true;
		}
		if (keys.size() >= DreamTableConfig.maxLoadedFarms()) {
			return false;
		}
		keys.add(key);
		markDirty();
		return true;
	}

	private void keepLoaded(MinecraftServer server) {
		loadedChunks.clear();
		int limit = DreamTableConfig.maxLoadedFarms();
		for (Map.Entry<UUID, Set<String>> entry : picked.entrySet()) {
			Set<String> keys = entry.getValue();
			if (keys.isEmpty() || limit <= 0 || (!DreamTableConfig.loadWhileOffline() && server.getPlayerManager().getPlayer(entry.getKey()) == null)) {
				continue;
			}
			Set<String> found = new LinkedHashSet<>();
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
			for (Located farm : located(server, entry.getKey())) {
				if (keys.contains(farm.key()) && found.size() < limit) {
					found.add(farm.key());
					load(farm.world(), farm.farm().box(), loadedChunks.computeIfAbsent(farm.world(), ignored -> new LongOpenHashSet()));
					reportIfFull(owner, farm);
				}
			}
			if (keys.retainAll(found)) {
				markDirty();
			}
		}
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
			Text name = FarmOptions.byId(farm.farm().farmId()).map(FarmOptions.FarmOption::title).orElse(Text.literal(farm.farm().farmId()));
			owner.sendMessage(Text.translatable("gui.the_dream_table.storage_full", name, where.getX(), where.getY(), where.getZ()), false);
		}
	}

	/** The player's finished farm with this My Farms key. */
	public Optional<Located> byKey(MinecraftServer server, UUID player, String key) {
		return located(server, player).stream().filter(farm -> farm.key().equals(key)).findFirst();
	}

	/** The player's finished farm at this place in the My Farms list, as last listed. */
	public Optional<Located> at(MinecraftServer server, UUID player, int index) {
		List<Located> farms = located(server, player);
		return index >= 0 && index < farms.size() ? Optional.of(farms.get(index)) : Optional.empty();
	}

	private static List<Located> located(MinecraftServer server, UUID player) {
		List<Located> farms = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			String dimension = world.getRegistryKey().getValue().toString();
			for (FarmAreas.Finished farm : FarmAreas.of(world).finishedBy(player)) {
				farms.add(new Located(dimension + "|" + farm.id(), world, farm));
			}
		}
		farms.sort(Comparator.comparingLong((Located farm) -> farm.farm().finishedAt()).reversed());
		return farms;
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

	private static FarmLoader read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
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
			for (int index = 0; index < list.size(); index++) {
				keys.add(list.getString(index));
			}
			loader.picked.put(id, keys);
		}
		return loader;
	}
}
