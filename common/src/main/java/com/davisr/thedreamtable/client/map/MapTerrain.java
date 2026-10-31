package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.OptionalInt;

/**
 * Reads the client's terrain for the map's range ring. A column whose chunk the client has not received is unknown, and draws
 * no edge until it arrives.
 */
public final class MapTerrain {
	private static final int RANGE = FarmGeometry.AIM_RANGE;
	private static final int SIDE = 2 * RANGE + 1;

	private MapTerrain() {
	}

	/**
	 * The ground blocks on the edge of the table's reach, which glow in map mode. The whole reach is scanned once when the ring is
	 * made; after that {@link #refresh} rescans a slice of columns per tick, so keeping it current never stalls a frame.
	 */
	public static final class Ring {
		private final BlockPos table;
		/** Each column's anchor Y, row by row from the reach's north-west corner. */
		private final int[] anchors = new int[SIDE * SIDE];
		private int next;
		private List<BlockPos> edge;

		public Ring(ClientWorld world, BlockPos table) {
			this.table = table.toImmutable();
			for (int index = 0; index < anchors.length; index++) {
				anchors[index] = scan(world, index);
			}
			edge = build();
		}

		public boolean isFor(BlockPos pos) {
			return table.equals(pos);
		}

		public List<BlockPos> edge() {
			return edge;
		}

		/** Rescans the next {@code columns} columns, wrapping round, and rebuilds the edge when any of them changed. */
		public void refresh(ClientWorld world, int columns) {
			boolean changed = false;
			for (int count = 0; count < columns; count++) {
				int anchor = scan(world, next);
				if (anchor != anchors[next]) {
					anchors[next] = anchor;
					changed = true;
				}
				next = (next + 1) % anchors.length;
			}
			if (changed) {
				edge = build();
			}
		}

		private int scan(ClientWorld world, int index) {
			return anchorY(world, table, table.getX() - RANGE + index / SIDE, table.getZ() - RANGE + index % SIDE);
		}

		private List<BlockPos> build() {
			return MapGround.rangeEdge(table, RANGE, (x, z) -> {
				int dx = x - table.getX() + RANGE;
				int dz = z - table.getZ() + RANGE;
				// Past the scanned square every column is out of reach anyway.
				return dx < 0 || dx >= SIDE || dz < 0 || dz >= SIDE ? MapGround.NO_GROUND : anchors[dx * SIDE + dz];
			});
		}
	}

	/** How many columns a ring rescans per tick so the whole reach is seen again about every {@code ticks} ticks. */
	public static int columnsPerTick(int ticks) {
		return (SIDE * SIDE + ticks - 1) / ticks;
	}

	/**
	 * The column's anchor Y, {@link MapGround#NO_GROUND} for none (or one past the world's top), or {@link MapGround#UNKNOWN}
	 * when its chunk is not loaded.
	 */
	private static int anchorY(ClientWorld world, BlockPos table, int x, int z) {
		if (!world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
			return MapGround.UNKNOWN;
		}
		// The heightmap is only a starting point: the client updates it with every block change it receives.
		int top = Math.min(table.getY() + RANGE, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z));
		OptionalInt y = MapGround.anchorY(probe(world), x, z, Math.max(table.getY() - RANGE, world.getBottomY()), top);
		return y.isPresent() && !world.isOutOfHeightLimit(y.getAsInt()) ? y.getAsInt() : MapGround.NO_GROUND;
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
