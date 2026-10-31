package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.client.hologram.PlacementOutline;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Reads the client's terrain for the map's range ring. A column whose chunk the client has not received counts as no ground.
 */
public final class MapTerrain {
	private static final int RANGE = FarmGeometry.AIM_RANGE;
	private static final int LOOP_POINTS = 128;

	private MapTerrain() {
	}

	/**
	 * The closed loop where the range reaches, following the terrain. Each column is scanned once per build: the ring's rays
	 * cross the same columns many times, and a column's anchor is the same every time.
	 */
	public static List<Vec3d> rangeLoop(ClientWorld world, BlockPos table) {
		Map<Long, Integer> columns = new HashMap<>();
		return PlacementOutline.rangeLoop(table, RANGE, LOOP_POINTS,
				(x, z) -> columns.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> anchorY(world, table, x, z)));
	}

	/**
	 * The column's anchor Y, or {@link Integer#MIN_VALUE} for none. The scan starts at the top of the reach rather than at the
	 * client's heightmap: that is only what the client last received, so a block placed above it would be missed.
	 */
	private static int anchorY(ClientWorld world, BlockPos table, int x, int z) {
		if (!world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
			return Integer.MIN_VALUE;
		}
		OptionalInt y = MapGround.anchorY(probe(world), x, z, table.getY() - RANGE, table.getY() + RANGE);
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
