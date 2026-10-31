package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.BlockPos;

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
}
