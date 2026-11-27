package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.block.DreamTableBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads the block states of a box. Only states are kept (no block entity data, no entities), air and Dream Table blocks are
 * left out as the importer leaves them out, and so is flowing liquid, which the importer skips because it comes from its source.
 * The world is reached through {@link Chunks}, so the same code counts on the client and reads on the server, and tests run without a world.
 */
public final class CaptureScan {
	/** One kept block, its place relative to the box's lowest corner. */
	public record Entry(int x, int y, int z, BlockState state) {
	}

	/** The kept blocks in order, or the problem that stopped the read. */
	public record Result(CaptureProblem problem, List<Entry> blocks) {
	}

	/** The chunks of a world: the state lookup of a chunk that is fully loaded right now, null for one that is not. It must never load or wait for a chunk. */
	@FunctionalInterface
	public interface Chunks {
		Function<BlockPos, BlockState> at(int chunkX, int chunkZ);
	}

	private CaptureScan() {
	}

	/**
	 * Reads {@code box} when every chunk it touches is loaded (all are looked up before the first block is read, and none is
	 * loaded to read it), and stops at once past {@link CaptureBox#MAX_BLOCKS} kept blocks.
	 */
	public static Result read(CaptureBox box, Chunks chunks) {
		int firstX = box.min().getX() >> 4;
		int firstZ = box.min().getZ() >> 4;
		int countX = (box.max().getX() >> 4) - firstX + 1;
		int countZ = (box.max().getZ() >> 4) - firstZ + 1;
		List<Function<BlockPos, BlockState>> lookups = new ArrayList<>(countX * countZ);
		for (int chunkX = 0; chunkX < countX; chunkX++) {
			for (int chunkZ = 0; chunkZ < countZ; chunkZ++) {
				Function<BlockPos, BlockState> lookup = chunks.at(firstX + chunkX, firstZ + chunkZ);
				if (lookup == null) {
					return new Result(CaptureProblem.of(CaptureProblem.Kind.UNLOADED), List.of());
				}
				lookups.add(lookup);
			}
		}
		List<Entry> blocks = new ArrayList<>();
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (int y = box.min().getY(); y <= box.max().getY(); y++) {
			for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
				for (int x = box.min().getX(); x <= box.max().getX(); x++) {
					BlockState state = lookups.get(((x >> 4) - firstX) * countZ + (z >> 4) - firstZ).apply(at.set(x, y, z));
					if (!kept(state)) {
						continue;
					}
					if (blocks.size() >= CaptureBox.MAX_BLOCKS) {
						return new Result(new CaptureProblem(CaptureProblem.Kind.TOO_MANY_BLOCKS, blocks.size() + 1L, CaptureBox.MAX_BLOCKS), List.of());
					}
					blocks.add(new Entry(x - box.min().getX(), y - box.min().getY(), z - box.min().getZ(), state));
				}
			}
		}
		if (blocks.isEmpty()) {
			return new Result(CaptureProblem.of(CaptureProblem.Kind.EMPTY), blocks);
		}
		return new Result(null, blocks);
	}

	private static boolean kept(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof DreamTableBlock) {
			return false;
		}
		boolean liquid = state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
		return !(liquid && state.contains(Properties.LEVEL_15) && state.get(Properties.LEVEL_15) != 0);
	}
}
