package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

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
	void theBoxMustComeWithinTheTablesReach() {
		assertTrue(CaptureTables.usable(PLAYER, TABLE, null));
		assertTrue(CaptureTables.usable(PLAYER, TABLE, CaptureBox.of(new BlockPos(20, 64, -3), new BlockPos(48, 70, 3))));
		assertFalse(CaptureTables.usable(PLAYER, TABLE, CaptureBox.of(new BlockPos(49, 64, -3), new BlockPos(60, 70, 3))));
		assertFalse(CaptureTables.usable(PLAYER, new BlockPos(100, 64, 0), null), "a table the player is too far from is not usable");
	}
}
