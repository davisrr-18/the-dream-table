package com.davisr.thedreamtable.farm;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;

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

	public static void keepTable(ServerWorld world, BlockPos table) {
		ChunkPos chunk = new ChunkPos(table);
		world.getChunkManager().addTicket(TICKET, chunk, TABLE_RADIUS, chunk);
	}

	/** Loads, or keeps loaded, the chunk of a block about to be placed or removed. */
	public static void keepSpot(ServerWorld world, BlockPos spot) {
		ChunkPos chunk = new ChunkPos(spot);
		world.getChunkManager().addTicket(TICKET, chunk, 0, chunk);
	}
}
