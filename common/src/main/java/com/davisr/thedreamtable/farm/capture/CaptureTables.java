package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Which Dream Table a capture is sent with. The server accepts a capture only when the player is within
 * {@link #MAX_PLAYER_DISTANCE} of the table and the box's nearest point is within the table's aim range, so the capture map mode
 * is centred on the nearest table the client knows within that distance and checks the box against its reach.
 */
public final class CaptureTables {
	/** How far the player may be from the table: the aim range plus the 16 blocks {@code CheckPlacementPayload} allows. */
	public static final double MAX_PLAYER_DISTANCE = FarmGeometry.AIM_RANGE + 16.0;

	private CaptureTables() {
	}

	public static boolean playerNear(Vec3d player, BlockPos table) {
		return player.squaredDistanceTo(table.toCenterPos()) <= MAX_PLAYER_DISTANCE * MAX_PLAYER_DISTANCE;
	}

	/** The tables within {@link #MAX_PLAYER_DISTANCE} of the player, nearest first. */
	public static List<BlockPos> byDistance(Vec3d player, Collection<BlockPos> tables) {
		return tables.stream().filter(table -> playerNear(player, table))
				.sorted(Comparator.comparingDouble(table -> player.squaredDistanceTo(table.toCenterPos()))).toList();
	}

	/** The table nearest to the player among those within {@link #MAX_PLAYER_DISTANCE} of them, or null when there is none. */
	public static BlockPos nearest(Vec3d player, Collection<BlockPos> tables) {
		return byDistance(player, tables).stream().findFirst().orElse(null);
	}

	/** True when the player is near the table and, given a box, the table's reach covers it. */
	public static boolean usable(Vec3d player, BlockPos table, CaptureBox box) {
		return playerNear(player, table) && (box == null || box.inReachOf(table));
	}
}
