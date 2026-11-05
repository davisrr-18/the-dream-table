package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmAreas;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Whether infinite water is near the table: a still water source with at least two of its four horizontal neighbours also
 * sources, so vanilla keeps it a source forever. Searched within {@link NearbyContainers#RADIUS} of the table, like Deliver from
 * Nearby. Only loaded chunks count, and water inside a Dream Table farm does not, so a built farm's pool can't supply the next one.
 */
public final class NearbyWater {
	/** What the search reads about one cell. The server and the client each answer from their own world. */
	public interface Cells {
		boolean loaded(BlockPos pos);

		FluidState fluid(BlockPos pos);

		boolean farmOwned(BlockPos pos);
	}

	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private NearbyWater() {
	}

	public static boolean nearby(Cells cells, BlockPos table) {
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int x = -NearbyContainers.RADIUS; x <= NearbyContainers.RADIUS; x++) {
			for (int y = -NearbyContainers.RADIUS; y <= NearbyContainers.RADIUS; y++) {
				for (int z = -NearbyContainers.RADIUS; z <= NearbyContainers.RADIUS; z++) {
					cursor.set(table, x, y, z);
					if (!NearbyContainers.withinRange(table, cursor) || !source(cells, cursor)) {
						continue;
					}
					int sides = 0;
					for (Direction side : SIDES) {
						if (source(cells, cursor.offset(side))) {
							sides++;
						}
					}
					if (sides >= 2) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The server's answer: the table's own world, with every Dream Table farm area excluded. */
	public static boolean sourceNear(ServerWorld world, BlockPos table) {
		return nearby(new Cells() {
			@Override
			public boolean loaded(BlockPos pos) {
				return world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
			}

			@Override
			public FluidState fluid(BlockPos pos) {
				return world.getFluidState(pos);
			}

			@Override
			public boolean farmOwned(BlockPos pos) {
				return FarmAreas.of(world).reserved(world, pos);
			}
		}, table);
	}

	/** Loaded first, so no unloaded cell is read, and farm areas last, since that lookup is the expensive one. */
	private static boolean source(Cells cells, BlockPos pos) {
		if (!cells.loaded(pos)) {
			return false;
		}
		FluidState fluid = cells.fluid(pos);
		return fluid.isStill() && fluid.isOf(Fluids.WATER) && !cells.farmOwned(pos);
	}
}
