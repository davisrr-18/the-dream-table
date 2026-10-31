package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.OptionalInt;

/**
 * What the map's range ring and aim need from the ground: the anchor a column yields, and the sphere of reach around the
 * table. Pure, so it can be tested without a world.
 */
public final class MapGround {
	private MapGround() {
	}

	/** What a block is for the aim: air, fluid and blocks without an outline pass the ray; the rest stop it. */
	public enum Cell {
		PASS,
		SOLID,
		REPLACEABLE,
		TABLE
	}

	/** A column's anchor Y when the column has no ground the aim could meet. */
	public static final int NO_GROUND = Integer.MIN_VALUE;
	/** A column's anchor Y when the client has not received its chunk: neither inside nor outside the reach, so it draws no edge. */
	public static final int UNKNOWN = Integer.MAX_VALUE;

	/** The cell at a position, as the world reports it. */
	public interface Probe {
		Cell at(int x, int y, int z);
	}

	/**
	 * The anchor Y of a column, scanning down from {@code maxY}: the first block the ray stops on decides. A solid block gives
	 * the cell above it, a replaceable one (grass, snow layer) its own cell, and a Dream Table gives none, as the aim does.
	 */
	public static OptionalInt anchorY(Probe probe, int x, int z, int minY, int maxY) {
		for (int y = maxY; y >= minY; y--) {
			Cell cell = probe.at(x, y, z);
			if (cell == Cell.SOLID) {
				return OptionalInt.of(y + 1);
			}
			if (cell == Cell.REPLACEABLE) {
				return OptionalInt.of(y);
			}
			if (cell == Cell.TABLE) {
				return OptionalInt.empty();
			}
		}
		return OptionalInt.empty();
	}

	/** Whether an anchor lies within {@code range} blocks of the table, measured like the server checks it. Never the table itself. */
	public static boolean inRange(BlockPos anchor, BlockPos table, int range) {
		return !anchor.equals(table) && anchor.getSquaredDistance(table) <= (double) range * range;
	}

	/**
	 * The ground blocks along the edge of the reach: one per column whose anchor is in range while a neighbouring column's is not
	 * (out of range or without ground). {@code anchorYAt} gives a column's anchor Y, {@link #NO_GROUND} for none, or
	 * {@link #UNKNOWN} when the client does not hold it yet, which never makes an edge; the table's own column counts as inside,
	 * so the table does not ring itself.
	 */
	public static List<BlockPos> rangeEdge(BlockPos table, int range, IntBinaryOperator anchorYAt) {
		List<BlockPos> edge = new ArrayList<>();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				int x = table.getX() + dx;
				int z = table.getZ() + dz;
				int y = anchorYAt.applyAsInt(x, z);
				if (!inside(table, range, x, y, z)) {
					continue;
				}
				if (outside(table, range, x + 1, z, anchorYAt) || outside(table, range, x - 1, z, anchorYAt)
						|| outside(table, range, x, z + 1, anchorYAt) || outside(table, range, x, z - 1, anchorYAt)) {
					edge.add(new BlockPos(x, y - 1, z));
				}
			}
		}
		return List.copyOf(edge);
	}

	private static boolean outside(BlockPos table, int range, int x, int z, IntBinaryOperator anchorYAt) {
		if (x == table.getX() && z == table.getZ()) {
			return false;
		}
		int y = anchorYAt.applyAsInt(x, z);
		return y != UNKNOWN && !inside(table, range, x, y, z);
	}

	private static boolean inside(BlockPos table, int range, int x, int y, int z) {
		return y != NO_GROUND && y != UNKNOWN && inRange(new BlockPos(x, y, z), table, range);
	}
}
