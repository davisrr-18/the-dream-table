package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline drawn around a farm spot while the player aims it: its colour says whether the server accepted the spot,
 * and its geometry is the farm's footprint drawn as twelve thin edges. Pure, so it can be tested without rendering.
 */
public final class PlacementOutline {
	private PlacementOutline() {
	}

	/** The outline colour. Each status keeps the colour the hologram already uses for it. */
	public enum Status {
		CHECKING(0.95F, 0.86F, 0.45F),
		VALID(0.35F, 0.9F, 0.45F),
		REFUSED(1.0F, 0.42F, 0.38F);

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
	 * The twelve edges of a box, each a thin box of the given thickness centred on the edge and running the full length
	 * of it. Order: the four edges along x, then the four along y, then the four along z; within each group the other two
	 * axes go low to high.
	 */
	public static List<Box> edges(Box box, double thickness) {
		double half = thickness / 2.0;
		List<Box> edges = new ArrayList<>(12);
		for (double y : new double[] {box.minY, box.maxY}) {
			for (double z : new double[] {box.minZ, box.maxZ}) {
				edges.add(new Box(box.minX, y - half, z - half, box.maxX, y + half, z + half));
			}
		}
		for (double x : new double[] {box.minX, box.maxX}) {
			for (double z : new double[] {box.minZ, box.maxZ}) {
				edges.add(new Box(x - half, box.minY, z - half, x + half, box.maxY, z + half));
			}
		}
		for (double x : new double[] {box.minX, box.maxX}) {
			for (double y : new double[] {box.minY, box.maxY}) {
				edges.add(new Box(x - half, y - half, box.minZ, x + half, y + half, box.maxZ));
			}
		}
		return List.copyOf(edges);
	}
}
