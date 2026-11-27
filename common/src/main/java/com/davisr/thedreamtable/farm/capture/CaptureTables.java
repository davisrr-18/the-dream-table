package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Whether the server would accept a capture with a given table: the player within {@link #MAX_PLAYER_DISTANCE} of it and the
 * box's nearest point within the table's aim range. The client sends the table the farm placement follows, and checks this first.
 */
public final class CaptureTables {
	/** How far the player may be from the table: the aim range plus the 16 blocks {@code CheckPlacementPayload} allows. */
	public static final double MAX_PLAYER_DISTANCE = FarmGeometry.AIM_RANGE + 16.0;

	private CaptureTables() {
	}

	public static boolean playerNear(Vec3d player, BlockPos table) {
		return player.squaredDistanceTo(table.toCenterPos()) <= MAX_PLAYER_DISTANCE * MAX_PLAYER_DISTANCE;
	}

	/** True when the player is near the table and, given a box, the table's reach covers it. */
	public static boolean usable(Vec3d player, BlockPos table, CaptureBox box) {
		return playerNear(player, table) && (box == null || box.inReachOf(table));
	}
}
