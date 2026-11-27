package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;

/**
 * Which Dream Table a capture is sent with. The server accepts a capture only when the player is within
 * {@link #MAX_PLAYER_DISTANCE} of the table and the box's nearest point is within the table's aim range, so the client picks the
 * nearest table that satisfies both.
 */
public final class CaptureTables {
	/** How far the player may be from the table: the aim range plus the 16 blocks {@code CheckPlacementPayload} allows. */
	public static final double MAX_PLAYER_DISTANCE = FarmGeometry.AIM_RANGE + 16.0;

	private CaptureTables() {
	}

	/**
	 * The table nearest to the player among those within {@link #MAX_PLAYER_DISTANCE} of them whose reach covers {@code box};
	 * with a null box, only the distance counts. Null when none qualifies.
	 */
	public static BlockPos nearest(Vec3d player, Collection<BlockPos> tables, CaptureBox box) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos table : tables) {
			double distance = player.squaredDistanceTo(table.toCenterPos());
			if (distance > MAX_PLAYER_DISTANCE * MAX_PLAYER_DISTANCE || (box != null && !box.inReachOf(table)) || distance >= bestDistance) {
				continue;
			}
			best = table;
			bestDistance = distance;
		}
		return best;
	}
}
