package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSelectionTest {
	private static final BlockPos FIRST = new BlockPos(10, 64, 10);

	@Test
	void theBoxFollowsTheAimedBlockUntilTheSecondClickFixesCornerTwoThere() {
		CaptureSelection selection = new CaptureSelection();
		assertNull(selection.box());
		selection.click(FIRST);
		assertTrue(selection.started());
		assertFalse(selection.fixed());
		assertEquals(CaptureBox.of(FIRST, FIRST), selection.box(), "before the crosshair moves, one block");

		selection.follow(new BlockPos(14, 66, 20));
		assertEquals(CaptureBox.of(FIRST, new BlockPos(14, 66, 20)), selection.box());
		selection.follow(null);
		assertEquals(CaptureBox.of(FIRST, new BlockPos(14, 66, 20)), selection.box(), "aiming at nothing keeps the last block");
		selection.follow(new BlockPos(5, 70, 3));
		assertEquals(CaptureBox.of(FIRST, new BlockPos(5, 70, 3)), selection.box());

		selection.click(new BlockPos(2, 64, 30));
		assertTrue(selection.fixed());
		assertEquals(CaptureBox.of(FIRST, new BlockPos(2, 64, 30)), selection.box());
		selection.follow(new BlockPos(99, 99, 99));
		assertEquals(CaptureBox.of(FIRST, new BlockPos(2, 64, 30)), selection.box(), "a fixed corner no longer follows");
	}

	@Test
	void aThirdClickStartsAgainAndAClickWithNoBlockChangesNothing() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(FIRST);
		selection.click(new BlockPos(14, 66, 20));
		selection.click(null);
		assertTrue(selection.fixed());
		BlockPos other = new BlockPos(1, 1, 1);
		selection.click(other);
		assertFalse(selection.fixed());
		assertEquals(CaptureBox.of(other, other), selection.box());
	}

	@Test
	void theTopAndBottomMoveFromTheFirstClickOnSoAnArrowKeyIsNeverIgnored() {
		CaptureSelection selection = new CaptureSelection();
		selection.moveTop(1);
		assertNull(selection.box(), "nothing to move before the first click");
		selection.click(FIRST);
		selection.moveTop(1);
		assertEquals(65, selection.box().max().getY(), "Up grows the box upward while it is still following the crosshair");
		selection.moveBottom(-1);
		assertEquals(63, selection.box().min().getY(), "Down grows it downward");
		selection.click(new BlockPos(14, 66, 20));
		assertEquals(63, selection.box().min().getY());
		assertEquals(67, selection.box().max().getY(), "the shifts stay on the fixed box: 64..66 plus one each way");
	}

	@Test
	void shrinkingNeverGoesBelowOneBlockOfHeight() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(FIRST);
		selection.click(new BlockPos(14, 66, 20));
		selection.moveTop(-1);
		selection.moveTop(-1);
		assertEquals(64, selection.box().max().getY());
		selection.moveTop(-1);
		assertEquals(64, selection.box().max().getY(), "the top may come down to the bottom, one block high");
		selection.moveBottom(1);
		assertEquals(64, selection.box().min().getY(), "the bottom may not pass the top");
		selection.moveTop(3);
		selection.moveBottom(2);
		assertEquals(66, selection.box().min().getY());
		assertEquals(67, selection.box().max().getY());
	}

	@Test
	void aSecondClickAtNothingFixesCornerTwoWhereThePreviewShowsIt() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(null);
		assertFalse(selection.started(), "no first corner from nothing");
		selection.click(FIRST);
		selection.follow(new BlockPos(14, 66, 20));
		selection.click(null);
		assertTrue(selection.fixed());
		assertEquals(CaptureBox.of(FIRST, new BlockPos(14, 66, 20)), selection.box());
	}

	@Test
	void shiftsMadeBeforeFixingAreKeptUnlessTheyWouldTurnTheNewBoxUpsideDown() {
		CaptureSelection kept = new CaptureSelection();
		kept.click(FIRST);
		kept.follow(new BlockPos(11, 70, 10));
		kept.moveTop(2);
		kept.moveBottom(-1);
		kept.click(new BlockPos(11, 70, 10));
		assertEquals(63, kept.box().min().getY());
		assertEquals(72, kept.box().max().getY());

		CaptureSelection dropped = new CaptureSelection();
		dropped.click(FIRST);
		dropped.follow(new BlockPos(11, 70, 10));
		dropped.moveBottom(5);
		dropped.click(new BlockPos(11, 64, 10));
		assertEquals(64, dropped.box().min().getY(), "the old shifts would have put the bottom above the top, so they are dropped");
		assertEquals(64, dropped.box().max().getY());
	}

	@Test
	void aBoxThePreviewTurnedUpsideDownKeepsOneBlockOfHeight() {
		CaptureSelection selection = new CaptureSelection();
		selection.click(FIRST);
		selection.follow(new BlockPos(11, 70, 10));
		selection.moveBottom(5);
		selection.follow(new BlockPos(11, 64, 10));
		CaptureBox box = selection.box();
		assertTrue(box.max().getY() >= box.min().getY());
		assertEquals(1, box.sizeY());
	}
}
