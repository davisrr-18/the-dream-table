package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline drawn around a farm spot while the player aims it: its colour says whether the server accepted the spot,
 * and its geometry is the farm's footprint. Pure, so it can be tested without rendering.
 */
public final class PlacementOutline {
	private PlacementOutline() {
	}

	/** The outline colour. Saturated on purpose, so they read on dark ground and water. */
	public enum Status {
		CHECKING(1.0F, 0.72F, 0.1F),
		VALID(0.1F, 0.95F, 0.35F),
		REFUSED(1.0F, 0.2F, 0.2F);

		private final float red;
		private final float green;
		private final float blue;

		Status(float red, float green, float blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
		}

		/** A new array with the red, green and blue components from 0 to 1. */
		public float[] rgb() {
			return new float[] {red, green, blue};
		}
	}

	/** Checking until the server answers; then valid when it named no problem, refused otherwise. A null problem counts as none. */
	public static Status status(boolean answered, String problem) {
		if (!answered) {
			return Status.CHECKING;
		}
		return problem == null || problem.isEmpty() ? Status.VALID : Status.REFUSED;
	}

	/** The blocks a farm occupies at this site, computed exactly as {@link FarmSite#footprint} does. */
	public static Box footprint(FarmTemplate template, BlockPos table, BlockPos anchor, Direction facing, boolean mirrored) {
		return new FarmSite(table, anchor, facing, mirrored).footprint(template);
	}

	/**
	 * The twelve edges of a box as flat ribbons that always face the camera, each half {@code halfWidthPerBlock} wide for every
	 * block between the camera and that end of the ribbon, so a line keeps one width on screen at any distance. Each ribbon is
	 * four corners, cut at the plane {@code near} blocks in front of the camera ({@code forward} is its view direction) so a
	 * line that passes behind the camera never stretches. Ends that were not cut run half a width past the corner, so the
	 * ribbons meet in solid corners.
	 */
	public static List<Vec3d[]> ribbons(Box box, Vec3d camera, Vec3d forward, double near, double halfWidthPerBlock) {
		List<Vec3d[]> ribbons = new ArrayList<>(12);
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		for (double y : ys) {
			for (double z : zs) {
				ribbon(ribbons, new Vec3d(xs[0], y, z), new Vec3d(xs[1], y, z), camera, forward, near, halfWidthPerBlock);
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				ribbon(ribbons, new Vec3d(x, ys[0], z), new Vec3d(x, ys[1], z), camera, forward, near, halfWidthPerBlock);
			}
		}
		for (double x : xs) {
			for (double y : ys) {
				ribbon(ribbons, new Vec3d(x, y, zs[0]), new Vec3d(x, y, zs[1]), camera, forward, near, halfWidthPerBlock);
			}
		}
		return List.copyOf(ribbons);
	}

	/**
	 * Four L-shaped corner brackets on the aimed block's cell, eight flat ribbons lying on the ground, each arm
	 * {@link #BRACKET_ARM} long. The outer side of every ribbon sits exactly on the block's edge and the ribbon lies inside the
	 * block, so the brackets hug the block like the game's own selection outline. Their half width widens with the distance but
	 * never passes {@code maxHalf}, so far away they stay slim.
	 */
	public static List<Vec3d[]> brackets(BlockPos anchor, Vec3d camera, Vec3d forward, double near, double halfWidthPerBlock, double maxHalf) {
		double y = anchor.getY() + 0.03;
		List<Vec3d[]> arms = new ArrayList<>(8);
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				double cx = anchor.getX() + dx;
				double cz = anchor.getZ() + dz;
				double half = Math.min(maxHalf, Math.max(camera.distanceTo(new Vec3d(cx, y, cz)), near) * halfWidthPerBlock);
				double sx = dx == 0 ? 1.0 : -1.0;
				double sz = dz == 0 ? 1.0 : -1.0;
				// The centre lines sit half a width inside the edges, and each arm starts half a width in, so the ribbon's own
				// extension at its start reaches the corner exactly.
				double lineX = cx + sx * half;
				double lineZ = cz + sz * half;
				ribbon(arms, new Vec3d(lineX, y, lineZ), new Vec3d(cx + sx * BRACKET_ARM, y, lineZ), camera, forward, near, halfWidthPerBlock, true, maxHalf);
				ribbon(arms, new Vec3d(lineX, y, lineZ), new Vec3d(lineX, y, cz + sz * BRACKET_ARM), camera, forward, near, halfWidthPerBlock, true, maxHalf);
			}
		}
		return List.copyOf(arms);
	}

	/**
	 * L-shaped brackets on all eight corners of a block, each corner three arms {@link #BRACKET_ARM} long along its edges, as
	 * ribbons turned to the camera, so the block is marked from every side. Widths follow {@link #brackets}.
	 */
	public static List<Vec3d[]> cornerBrackets(BlockPos block, Vec3d camera, Vec3d forward, double near, double halfWidthPerBlock, double maxHalf) {
		List<Vec3d[]> arms = new ArrayList<>(24);
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					Vec3d corner = new Vec3d(block.getX() + dx, block.getY() + dy, block.getZ() + dz);
					double sx = dx == 0 ? BRACKET_ARM : -BRACKET_ARM;
					double sy = dy == 0 ? BRACKET_ARM : -BRACKET_ARM;
					double sz = dz == 0 ? BRACKET_ARM : -BRACKET_ARM;
					ribbon(arms, corner, corner.add(sx, 0, 0), camera, forward, near, halfWidthPerBlock, false, maxHalf);
					ribbon(arms, corner, corner.add(0, sy, 0), camera, forward, near, halfWidthPerBlock, false, maxHalf);
					ribbon(arms, corner, corner.add(0, 0, sz), camera, forward, near, halfWidthPerBlock, false, maxHalf);
				}
			}
		}
		return List.copyOf(arms);
	}

	/** Length of a bracket arm, in blocks. */
	private static final double BRACKET_ARM = 0.3;

	/**
	 * A vertical ribbon rising from the middle of the aimed block, turned to the camera, so the spot can be found from far
	 * away. Its corners go bottom left, bottom right, top right, top left; the renderer fades it out toward the top.
	 */
	public static List<Vec3d[]> beam(BlockPos anchor, double height, Vec3d camera, Vec3d forward, double near, double halfWidthPerBlock, double maxHalf) {
		List<Vec3d[]> beam = new ArrayList<>(1);
		double x = anchor.getX() + 0.5;
		double z = anchor.getZ() + 0.5;
		ribbon(beam, new Vec3d(x, anchor.getY(), z), new Vec3d(x, anchor.getY() + height, z), camera, forward, near, halfWidthPerBlock, false, maxHalf);
		return List.copyOf(beam);
	}

	private static void ribbon(List<Vec3d[]> out, Vec3d start, Vec3d end, Vec3d camera, Vec3d forward, double near, double perBlock) {
		ribbon(out, start, end, camera, forward, near, perBlock, false, Double.MAX_VALUE);
	}

	/** {@code flat} lays the ribbon in the horizontal plane instead of turning it to the camera, as the cross lies on the ground. */
	private static void ribbon(List<Vec3d[]> out, Vec3d start, Vec3d end, Vec3d camera, Vec3d forward, double near, double perBlock, boolean flat, double maxHalf) {
		double depthStart = start.subtract(camera).dotProduct(forward);
		double depthEnd = end.subtract(camera).dotProduct(forward);
		if (depthStart < near && depthEnd < near) {
			return;
		}
		boolean cutStart = depthStart < near;
		boolean cutEnd = depthEnd < near;
		Vec3d a = cutStart ? start.lerp(end, (near - depthStart) / (depthEnd - depthStart)) : start;
		Vec3d b = cutEnd ? end.lerp(start, (near - depthEnd) / (depthStart - depthEnd)) : end;
		Vec3d along = b.subtract(a);
		if (along.lengthSquared() < 1.0E-12) {
			return;
		}
		double widthA = Math.min(maxHalf, Math.max(a.distanceTo(camera), near) * perBlock);
		double widthB = Math.min(maxHalf, Math.max(b.distanceTo(camera), near) * perBlock);
		Vec3d unit = along.normalize();
		if (!cutStart) {
			a = a.subtract(unit.multiply(widthA));
		}
		if (!cutEnd) {
			b = b.add(unit.multiply(widthB));
		}
		Vec3d side = flat ? new Vec3d(0, 1, 0).crossProduct(unit) : a.add(b).multiply(0.5).subtract(camera).crossProduct(unit);
		if (side.lengthSquared() < 1.0E-12) {
			return;
		}
		side = side.normalize();
		out.add(new Vec3d[] {
				a.add(side.multiply(widthA)), a.subtract(side.multiply(widthA)),
				b.subtract(side.multiply(widthB)), b.add(side.multiply(widthB))});
	}
}
