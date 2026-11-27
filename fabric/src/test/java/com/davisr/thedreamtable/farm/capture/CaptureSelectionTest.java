package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSelectionTest {
	private static final BlockPos A = new BlockPos(10, 64, 10);
	private static final BlockPos B = new BlockPos(14, 66, 20);

	@Test
	void theBoxFollowsTheAimUntilTheSecondCornerIsClicked() {
		CaptureSelection selection = new CaptureSelection();
		assertNull(selection.box(A));
		selection.click(A);
		assertEquals(CaptureBox.of(A, B), selection.box(B));
		assertEquals(CaptureBox.of(A, A), selection.box(null));
		selection.click(B);
		assertTrue(selection.fixed());
		assertEquals(CaptureBox.of(A, B), selection.box(new BlockPos(99, 99, 99)));
	}

	@Test
	void aThirdClickStartsAgainAndAClickOnNothingChangesNothing() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(A);
		selection.click(B);
		selection.click(null);
		assertTrue(selection.fixed());
		selection.click(new BlockPos(1, 1, 1));
		assertFalse(selection.fixed());
		assertEquals(CaptureBox.of(new BlockPos(1, 1, 1), A), selection.box(A));
	}

	@Test
	void topAndBottomMoveOnlyOnAFixedBoxAndNeverCross() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(A);
		selection.moveTop(3);
		assertEquals(64, selection.box(A).max().getY());
		selection.click(B);
		selection.moveTop(2);
		selection.moveBottom(-1);
		assertEquals(63, selection.box(null).min().getY());
		assertEquals(68, selection.box(null).max().getY());
		selection.moveBottom(100);
		selection.moveTop(-100);
		assertEquals(63, selection.box(null).min().getY());
		assertEquals(68, selection.box(null).max().getY());
		selection.moveTop(-5);
		assertEquals(63, selection.box(null).max().getY(), "the top may come down to the bottom, one block high");
		selection.moveTop(-1);
		assertEquals(63, selection.box(null).max().getY());
		selection.moveBottom(1);
		assertEquals(63, selection.box(null).min().getY());
	}
}
