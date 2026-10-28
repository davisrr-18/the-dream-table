package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPlacementTest {
	private static final BlockPos ANCHOR = new BlockPos(7, 64, -3);

	@Test
	void mirrorSurvivesSavingAndSyncing() {
		FarmPlacement placement = new FarmPlacement();
		placement.set(ANCHOR, Direction.WEST, true);
		NbtCompound nbt = new NbtCompound();
		placement.write(nbt);

		FarmPlacement restored = new FarmPlacement();
		restored.read(nbt);

		assertTrue(restored.mirrored());
		assertEquals(Direction.WEST, restored.facing());
		assertEquals(ANCHOR, restored.anchor());
	}

	@Test
	void placementSavedWithoutTheTagIsNotMirrored() {
		FarmPlacement plain = new FarmPlacement();
		plain.set(ANCHOR, Direction.NORTH, false);
		NbtCompound nbt = new NbtCompound();
		plain.write(nbt);
		assertFalse(nbt.contains("FarmMirror"), "an unmirrored placement writes what 1.0.0 wrote");

		FarmPlacement restored = new FarmPlacement();
		restored.set(ANCHOR, Direction.EAST, true);
		restored.read(nbt);
		assertFalse(restored.mirrored(), "reading a save without the tag resets the flag");
		assertEquals(Direction.NORTH, restored.facing());
	}

	@Test
	void clearingForgetsTheMirror() {
		FarmPlacement placement = new FarmPlacement();
		placement.set(ANCHOR, Direction.EAST, true);
		placement.clear();
		assertFalse(placement.mirrored());
		assertFalse(placement.placed());
	}
}
