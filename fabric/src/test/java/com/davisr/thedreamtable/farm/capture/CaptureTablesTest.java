package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureTablesTest {
	private static final Vec3d PLAYER = new Vec3d(0.5, 64, 0.5);
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);

	@Test
	void thePlayerMustBeWithinTheServersDistanceOfTheTable() {
		assertTrue(CaptureTables.playerNear(PLAYER, new BlockPos(63, 64, 0)));
		assertFalse(CaptureTables.playerNear(PLAYER, new BlockPos(65, 64, 0)), "past 64 blocks the server refuses the player");
	}

	@Test
	void theNearestTableWithinTheServersDistanceIsChosen() {
		BlockPos near = new BlockPos(10, 64, 0);
		BlockPos far = new BlockPos(40, 64, 0);
		assertEquals(near, CaptureTables.nearest(PLAYER, List.of(far, near)));
		assertEquals(far, CaptureTables.nearest(PLAYER, List.of(far)));
		assertEquals(new BlockPos(63, 64, 0), CaptureTables.nearest(PLAYER, List.of(new BlockPos(63, 64, 0))));
		assertNull(CaptureTables.nearest(PLAYER, List.of(new BlockPos(65, 64, 0))), "past 64 blocks the server refuses the player");
		assertNull(CaptureTables.nearest(PLAYER, List.of()));
	}

	@Test
	void tablesComeNearestFirstSoACandidateTheMapCannotStartAtCanBePassedOver() {
		BlockPos a = new BlockPos(30, 64, 0);
		BlockPos b = new BlockPos(5, 64, 5);
		BlockPos c = new BlockPos(50, 64, 0);
		BlockPos tooFar = new BlockPos(80, 64, 0);
		assertEquals(List.of(b, a, c), CaptureTables.byDistance(PLAYER, List.of(a, tooFar, c, b)));
		assertEquals(List.of(), CaptureTables.byDistance(PLAYER, List.of(tooFar)));
	}

	@Test
	void theBoxMustComeWithinTheTablesReach() {
		assertTrue(CaptureTables.usable(PLAYER, TABLE, null));
		assertTrue(CaptureTables.usable(PLAYER, TABLE, CaptureBox.of(new BlockPos(20, 64, -3), new BlockPos(48, 70, 3))));
		assertFalse(CaptureTables.usable(PLAYER, TABLE, CaptureBox.of(new BlockPos(49, 64, -3), new BlockPos(60, 70, 3))));
		assertFalse(CaptureTables.usable(PLAYER, new BlockPos(100, 64, 0), null), "a table the player is too far from is not usable");
	}
}
