package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSelectionTest {
	private static final BlockPos AIMED = new BlockPos(10, 64, 10);
	private static final BlockPos FEET = new BlockPos(10, 65, 10);

	@Test
	void cornerTwoFollowsTheBodyUntilTheSecondClickFixesIt() {
		CaptureSelection selection = new CaptureSelection();
		assertNull(selection.box());
		selection.click(AIMED, FEET);
		assertTrue(selection.started());
		assertFalse(selection.fixed());
		assertEquals(CaptureBox.of(AIMED, FEET), selection.box());

		selection.follow(new BlockPos(14, 66, 20));
		assertEquals(CaptureBox.of(AIMED, new BlockPos(14, 66, 20)), selection.box());
		selection.follow(new BlockPos(5, 70, 3));
		assertEquals(CaptureBox.of(AIMED, new BlockPos(5, 70, 3)), selection.box());

		selection.click(AIMED, new BlockPos(2, 64, 30));
		assertTrue(selection.fixed());
		assertEquals(CaptureBox.of(AIMED, new BlockPos(2, 64, 30)), selection.box());
		selection.follow(new BlockPos(99, 99, 99));
		assertEquals(CaptureBox.of(AIMED, new BlockPos(2, 64, 30)), selection.box(), "a fixed corner no longer follows");
	}

	@Test
	void aThirdClickStartsAgainAndAClickWithoutAPositionChangesNothing() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(AIMED, FEET);
		selection.click(AIMED, new BlockPos(14, 66, 20));
		selection.click(null, FEET);
		selection.click(AIMED, null);
		assertTrue(selection.fixed());
		BlockPos other = new BlockPos(1, 1, 1);
		selection.click(other, new BlockPos(3, 1, 3));
		assertFalse(selection.fixed());
		assertEquals(CaptureBox.of(other, new BlockPos(3, 1, 3)), selection.box());
	}

	@Test
	void topAndBottomMoveOnlyOnAFixedBoxAndNeverCross() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(AIMED, FEET);
		selection.moveTop(3);
		assertEquals(65, selection.box().max().getY());
		selection.click(AIMED, new BlockPos(14, 66, 20));
		selection.moveTop(2);
		selection.moveBottom(-1);
		assertEquals(63, selection.box().min().getY());
		assertEquals(68, selection.box().max().getY());
		selection.moveBottom(100);
		selection.moveTop(-100);
		assertEquals(63, selection.box().min().getY());
		assertEquals(68, selection.box().max().getY());
		selection.moveTop(-5);
		assertEquals(63, selection.box().max().getY(), "the top may come down to the bottom, one block high");
		selection.moveTop(-1);
		assertEquals(63, selection.box().max().getY());
		selection.moveBottom(1);
		assertEquals(63, selection.box().min().getY());
	}
}
