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
 * and its geometry is the farm's footprint drawn as twelve thin edges. Pure, so it can be tested without rendering.
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
	 * The twelve edges of a box as solid bars that keep the same apparent width on screen: each edge is cut into pieces of at
	 * most {@code segment} blocks and a piece is {@code perBlock} thick for every block between it and the camera, never
	 * thinner than {@code minThickness} nor thicker than {@link #MAX_THICKNESS}. Every edge runs half a thickness past each
	 * end, so the bars meet in solid corners. Order: the edges along x, then y, then z, each from low to high.
	 */
	public static List<Box> bars(Box box, Vec3d camera, double minThickness, double perBlock, double segment) {
		List<Box> bars = new ArrayList<>();
		for (double y : new double[] {box.minY, box.maxY}) {
			for (double z : new double[] {box.minZ, box.maxZ}) {
				bar(bars, Direction.Axis.X, box.minX, box.maxX, y, z, camera, minThickness, perBlock, segment);
			}
		}
		for (double x : new double[] {box.minX, box.maxX}) {
			for (double z : new double[] {box.minZ, box.maxZ}) {
				bar(bars, Direction.Axis.Y, box.minY, box.maxY, x, z, camera, minThickness, perBlock, segment);
			}
		}
		for (double x : new double[] {box.minX, box.maxX}) {
			for (double y : new double[] {box.minY, box.maxY}) {
				bar(bars, Direction.Axis.Z, box.minZ, box.maxZ, x, y, camera, minThickness, perBlock, segment);
			}
		}
		return List.copyOf(bars);
	}

	/** The widest a bar gets, however far the camera is. */
	public static final double MAX_THICKNESS = 0.6;

	/** One edge along {@code axis} from {@code low} to {@code high}; {@code a} and {@code b} are the other two coordinates in x, y, z order. */
	private static void bar(List<Box> bars, Direction.Axis axis, double low, double high, double a, double b,
			Vec3d camera, double minThickness, double perBlock, double segment) {
		int pieces = Math.max(1, (int) Math.ceil((high - low) / segment));
		double step = (high - low) / pieces;
		for (int piece = 0; piece < pieces; piece++) {
			double from = low + piece * step;
			double to = from + step;
			double mid = (from + to) / 2.0;
			Vec3d centre = switch (axis) {
				case X -> new Vec3d(mid, a, b);
				case Y -> new Vec3d(a, mid, b);
				case Z -> new Vec3d(a, b, mid);
			};
			double thickness = Math.min(MAX_THICKNESS, Math.max(minThickness, camera.distanceTo(centre) * perBlock));
			double half = thickness / 2.0;
			double start = piece == 0 ? from - half : from;
			double end = piece == pieces - 1 ? to + half : to;
			bars.add(switch (axis) {
				case X -> new Box(start, a - half, b - half, end, a + half, b + half);
				case Y -> new Box(a - half, start, b - half, a + half, end, b + half);
				case Z -> new Box(a - half, b - half, start, a + half, b + half, end);
			});
		}
	}
}
