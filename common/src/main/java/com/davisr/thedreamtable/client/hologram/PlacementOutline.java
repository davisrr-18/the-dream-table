package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

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
}
