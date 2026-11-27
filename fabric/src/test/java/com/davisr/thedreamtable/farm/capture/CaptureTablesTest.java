package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaptureTablesTest {
	private static final Vec3d PLAYER = new Vec3d(0.5, 64, 0.5);

	@Test
	void theNearestTableWithinTheServersDistanceIsChosen() {
		BlockPos near = new BlockPos(10, 64, 0);
		BlockPos far = new BlockPos(40, 64, 0);
		assertEquals(near, CaptureTables.nearest(PLAYER, List.of(far, near), null));
		assertEquals(far, CaptureTables.nearest(PLAYER, List.of(far), null));
		assertNull(CaptureTables.nearest(PLAYER, List.of(new BlockPos(65, 64, 0)), null), "past 64 blocks the server refuses the player");
		assertEquals(new BlockPos(63, 64, 0), CaptureTables.nearest(PLAYER, List.of(new BlockPos(63, 64, 0)), null));
		assertNull(CaptureTables.nearest(PLAYER, List.of(), null));
	}

	@Test
	void aTableWhoseReachMissesTheBoxIsPassedOver() {
		BlockPos near = new BlockPos(0, 64, 0);
		BlockPos farther = new BlockPos(60, 64, 0);
		CaptureBox boxByTheFarOne = CaptureBox.of(new BlockPos(100, 64, -3), new BlockPos(110, 70, 3));
		assertEquals(farther, CaptureTables.nearest(PLAYER, List.of(near, farther), boxByTheFarOne), "the near table's 48 blocks stop short of the box");
		CaptureBox boxByBoth = CaptureBox.of(new BlockPos(20, 64, -3), new BlockPos(30, 70, 3));
		assertEquals(near, CaptureTables.nearest(PLAYER, List.of(near, farther), boxByBoth));
		assertNull(CaptureTables.nearest(PLAYER, List.of(near), boxByTheFarOne));
	}
}
