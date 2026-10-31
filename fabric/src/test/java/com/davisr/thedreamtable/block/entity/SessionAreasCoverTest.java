package com.davisr.thedreamtable.block.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A farm whose area holds its own table is refused, wherever in the area the table stands. */
class SessionAreasCoverTest {
	/** The area of blocks 0..4 on x and z and 64..69 on y, as a footprint is built. */
	private static final Box AREA = new Box(0, 64, 0, 5, 70, 5);

	@Test
	void aTableInsideTheAreaIsCovered() {
		assertTrue(SessionAreas.covers(AREA, new BlockPos(2, 66, 2)));
	}

	@Test
	void aTableOnTheAreasCornerBlocksIsCovered() {
		assertTrue(SessionAreas.covers(AREA, new BlockPos(0, 64, 0)));
		assertTrue(SessionAreas.covers(AREA, new BlockPos(4, 69, 4)));
	}

	@Test
	void aTableRightNextToTheAreaIsNotCovered() {
		assertFalse(SessionAreas.covers(AREA, new BlockPos(5, 64, 0)));
		assertFalse(SessionAreas.covers(AREA, new BlockPos(0, 70, 0)));
		assertFalse(SessionAreas.covers(AREA, new BlockPos(-1, 66, 2)));
	}
}
