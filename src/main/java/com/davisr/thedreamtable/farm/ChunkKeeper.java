package com.davisr.thedreamtable.farm;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the chunks a running build needs loaded while its owner is online but far away: the table's chunk, so it keeps
 * ticking, and the chunk of the block being placed. Tickets expire on their own a few seconds after the last renewal,
 * so a paused, finished or abandoned build lets its chunks unload.
 */
public final class ChunkKeeper {
	private static final int EXPIRY_TICKS = 100;
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("the_dream_table", Comparator.comparingLong(ChunkPos::toLong), EXPIRY_TICKS);
	/** Radius 1 makes the table's chunk block-ticking, which is all a block entity needs, and loads only its 3x3 neighbors. */
	private static final int TABLE_RADIUS = 1;

	private ChunkKeeper() {
	}

	/** A chunk renewed this recently is skipped, since a fast build asks for the same chunk many times a tick. */
	private static final int RENEW_TICKS = 20;
	private static final Map<ServerWorld, Long2LongOpenHashMap> RENEWED = new WeakHashMap<>();

	public static void keepTable(ServerWorld world, BlockPos table) {
		ChunkPos chunk = new ChunkPos(table);
		world.getChunkManager().addTicket(TICKET, chunk, TABLE_RADIUS, chunk);
	}

	/** Loads, or keeps loaded, the chunk of a block about to be placed or removed. */
	public static void keepSpot(ServerWorld world, BlockPos spot) {
		ChunkPos chunk = new ChunkPos(spot);
		Long2LongOpenHashMap renewed = RENEWED.computeIfAbsent(world, ignored -> new Long2LongOpenHashMap());
		long now = world.getTime();
		long last = renewed.getOrDefault(chunk.toLong(), Long.MIN_VALUE);
		if (now - last < RENEW_TICKS && world.isChunkLoaded(chunk.x, chunk.z)) {
			return;
		}
		if (renewed.size() > 4096) {
			renewed.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() >= EXPIRY_TICKS);
		}
		renewed.put(chunk.toLong(), now);
		world.getChunkManager().addTicket(TICKET, chunk, 0, chunk);
	}
}
