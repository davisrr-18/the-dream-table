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

	private static void ribbon(List<Vec3d[]> out, Vec3d start, Vec3d end, Vec3d camera, Vec3d forward, double near, double perBlock) {
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
		double widthA = Math.max(a.distanceTo(camera), near) * perBlock;
		double widthB = Math.max(b.distanceTo(camera), near) * perBlock;
		Vec3d unit = along.normalize();
		if (!cutStart) {
			a = a.subtract(unit.multiply(widthA));
		}
		if (!cutEnd) {
			b = b.add(unit.multiply(widthB));
		}
		Vec3d side = a.add(b).multiply(0.5).subtract(camera).crossProduct(unit);
		if (side.lengthSquared() < 1.0E-12) {
			return;
		}
		side = side.normalize();
		out.add(new Vec3d[] {
				a.add(side.multiply(widthA)), a.subtract(side.multiply(widthA)),
				b.subtract(side.multiply(widthB)), b.add(side.multiply(widthB))});
	}
}
