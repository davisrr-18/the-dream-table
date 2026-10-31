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
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads the client's terrain for map mode: the spot under the map cursor, and the ground the range circle follows.
 * A column whose chunk the client has not received counts as no ground.
 */
public final class MapTerrain {
	private static final int RANGE = FarmGeometry.AIM_RANGE;
	private static final int LOOP_POINTS = 128;

	private MapTerrain() {
	}

	/** The anchor under the map cursor, or empty when the cursor is over no loaded ground or outside the range. */
	public static Optional<BlockPos> cursorAnchor(ClientWorld world, BlockPos table, Vec3d camera, double ndcX, double ndcY, double tanHalfFovY, double aspect) {
		Optional<MapGround.Ground> ground = MapGround.pick(camera.x, camera.y, camera.z, ndcX, ndcY, tanHalfFovY, aspect,
				(x, z) -> surfaceY(world, table, (int) Math.floor(x), (int) Math.floor(z)));
		return ground.flatMap(g -> MapGround.anchorAt(probe(world), table, (int) Math.floor(g.x()), (int) Math.floor(g.z()), RANGE));
	}

	/** The closed loop where the range reaches, following the terrain; rebuilt now and then, not every frame. */
	public static List<Vec3d> rangeLoop(ClientWorld world, BlockPos table) {
		return PlacementOutline.rangeLoop(table, RANGE, LOOP_POINTS, (x, z) -> {
			OptionalInt y = anchorY(world, table, x, z);
			return y.isPresent() ? y.getAsInt() : Integer.MIN_VALUE;
		});
	}

	private static double surfaceY(ClientWorld world, BlockPos table, int x, int z) {
		OptionalInt y = anchorY(world, table, x, z);
		return y.isPresent() ? y.getAsInt() : Double.NaN;
	}

	private static OptionalInt anchorY(ClientWorld world, BlockPos table, int x, int z) {
		if (!world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
			return OptionalInt.empty();
		}
		int top = Math.min(table.getY() + RANGE, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z));
		return MapGround.anchorY(probe(world), x, z, table.getY() - RANGE, top);
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
