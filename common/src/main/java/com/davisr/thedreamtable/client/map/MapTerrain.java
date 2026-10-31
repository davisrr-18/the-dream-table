package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Reads the client's terrain for the map's range ring. A column whose chunk the client has not received counts as no ground.
 */
public final class MapTerrain {
	private static final int RANGE = FarmGeometry.AIM_RANGE;

	private MapTerrain() {
	}

	/** The ground blocks on the edge of the table's reach, which glow in map mode. Each column is scanned once per build. */
	public static List<BlockPos> rangeEdge(ClientWorld world, BlockPos table) {
		Map<Long, Integer> columns = new HashMap<>();
		return MapGround.rangeEdge(table, RANGE,
				(x, z) -> columns.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> anchorY(world, table, x, z)));
	}

	/**
	 * The column's anchor Y, or {@link Integer#MIN_VALUE} for none.
	 */
	private static int anchorY(ClientWorld world, BlockPos table, int x, int z) {
		if (!world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
			return Integer.MIN_VALUE;
		}
		// The heightmap is only a starting point: the client updates it with every block change it receives.
		int top = Math.min(table.getY() + RANGE, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z));
		OptionalInt y = MapGround.anchorY(probe(world), x, z, table.getY() - RANGE, top);
		return y.isPresent() ? y.getAsInt() : Integer.MIN_VALUE;
	}

	/** What a ray that ignores fluids would meet in the cell, as the crosshair aim does. */
	private static MapGround.Probe probe(ClientWorld world) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		return (x, y, z) -> {
			pos.set(x, y, z);
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || state.getBlock() instanceof FluidBlock) {
				return MapGround.Cell.PASS;
			}
			if (state.getBlock() instanceof DreamTableBlock) {
				return MapGround.Cell.TABLE;
			}
			if (state.getOutlineShape(world, pos).isEmpty()) {
				return MapGround.Cell.PASS;
			}
			return state.isReplaceable() ? MapGround.Cell.REPLACEABLE : MapGround.Cell.SOLID;
		};
	}
}
