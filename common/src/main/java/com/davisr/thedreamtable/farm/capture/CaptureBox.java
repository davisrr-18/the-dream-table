package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** The blocks between two corners, both included, with the size limits a capture shares with the importer. */
public record CaptureBox(BlockPos min, BlockPos max) {
	public static final int MAX_SPAN = SchematicReader.MAX_SPAN;
	public static final int MAX_HEIGHT = SchematicReader.MAX_HEIGHT;
	public static final int MAX_BLOCKS = SchematicReader.MAX_BLOCKS;
	/** The most cells a capture walks: the server reads every one on its own thread, so this bounds the time, far past any farm's box. */
	public static final long MAX_VOLUME = 1L << 20;

	public static CaptureBox of(BlockPos first, BlockPos second) {
		return new CaptureBox(
				new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())),
				new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())));
	}

	public int sizeX() {
		return max.getX() - min.getX() + 1;
	}

	public int sizeY() {
		return max.getY() - min.getY() + 1;
	}

	public int sizeZ() {
		return max.getZ() - min.getZ() + 1;
	}

	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}

	/** The box as the game draws an outline: the far corner is the edge of its block. */
	public Box outline() {
		return new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
	}

	/** The squared distance from the table to the nearest point of the box. */
	public double nearestSquaredDistance(BlockPos table) {
		double x = Math.max(Math.max(min.getX() - table.getX(), table.getX() - max.getX()), 0);
		double y = Math.max(Math.max(min.getY() - table.getY(), table.getY() - max.getY()), 0);
		double z = Math.max(Math.max(min.getZ() - table.getZ(), table.getZ() - max.getZ()), 0);
		return x * x + y * y + z * z;
	}

	/** What is wrong with the size or place of the box before any block is read, null when nothing is. The sides are measured in longs, so far-apart corners can't overflow. */
	public CaptureProblem sizeProblem(int bottomY, int topY) {
		long wide = Math.max((long) max.getX() - min.getX() + 1, (long) max.getZ() - min.getZ() + 1);
		long tall = (long) max.getY() - min.getY() + 1;
		if (wide > MAX_SPAN) {
			return new CaptureProblem(CaptureProblem.Kind.TOO_WIDE, wide, MAX_SPAN);
		}
		if (min.getY() < bottomY || max.getY() >= topY) {
			return CaptureProblem.of(CaptureProblem.Kind.OUT_OF_WORLD);
		}
		if (tall > MAX_HEIGHT) {
			return new CaptureProblem(CaptureProblem.Kind.TOO_TALL, tall, MAX_HEIGHT);
		}
		// Every side is within its limit here, so the int sizes can't overflow.
		return volume() > MAX_VOLUME ? new CaptureProblem(CaptureProblem.Kind.TOO_BIG, volume(), MAX_VOLUME) : null;
	}

	/** Whether the table's reach covers the box: its nearest point is within the aim range, as every spot map mode can aim at is. */
	public boolean inReachOf(BlockPos table) {
		return nearestSquaredDistance(table) <= (double) FarmGeometry.AIM_RANGE * FarmGeometry.AIM_RANGE;
	}
}
