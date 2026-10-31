package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.DoubleBinaryOperator;

/**
 * Where a farm spot can go when the camera looks straight down at the map: the anchor the aim would pick in a column, the
 * sphere of reach around the table, and the ground under a point on the screen. Pure, so it can be tested without a world.
 */
public final class MapGround {
	/** Enough passes for the ground under the picked point to settle when the terrain slopes gently. */
	private static final int PICK_ITERATIONS = 4;

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

	/** A point on the ground picked on the map. */
	public record Ground(double x, double z, double y) {
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

	/** The anchor a column yields within {@code range} blocks of the table's height, or nothing when it is out of reach. */
	public static Optional<BlockPos> anchorAt(Probe probe, BlockPos table, int x, int z, int range) {
		OptionalInt y = anchorY(probe, x, z, table.getY() - range, table.getY() + range);
		if (y.isEmpty()) {
			return Optional.empty();
		}
		BlockPos anchor = new BlockPos(x, y.getAsInt(), z);
		return inRange(anchor, table, range) ? Optional.of(anchor) : Optional.empty();
	}

	/**
	 * The ground under a screen point with the camera looking straight down, north at the top and east at the right. The
	 * point's ground height is unknown until it is found, so the search starts from the ground under the camera and repeats:
	 * project the point at the current height, then read the height there. {@code surfaceY} gives the ground at a horizontal
	 * position, or NaN for none; the camera must stay above it.
	 */
	public static Optional<Ground> pick(double camX, double camY, double camZ, double ndcX, double ndcY, double tanHalfFovY, double aspect, DoubleBinaryOperator surfaceY) {
		double ground = surfaceY.applyAsDouble(camX, camZ);
		double x = camX;
		double z = camZ;
		for (int i = 0; i < PICK_ITERATIONS; i++) {
			if (!belowCamera(camY, ground)) {
				return Optional.empty();
			}
			double depth = camY - ground;
			x = camX + ndcX * tanHalfFovY * aspect * depth;
			z = camZ - ndcY * tanHalfFovY * depth;
			ground = surfaceY.applyAsDouble(x, z);
		}
		return belowCamera(camY, ground) ? Optional.of(new Ground(x, z, ground)) : Optional.empty();
	}

	/** False for NaN too, which {@code surfaceY} uses for no ground. */
	private static boolean belowCamera(double camY, double ground) {
		return camY - ground > 0;
	}
}
