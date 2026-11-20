package com.davisr.thedreamtable.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A take-apart whose farm file is gone leaves the blocks and tells the player, but only for a started build that is not done. */
class DiscardNoticeTest {
	@Test
	void aStartedBuildWhoseFarmFileIsGoneGetsTheNotice() {
		assertTrue(TableSession.needsTemplateGoneNotice(true, true, false, 5));
	}

	@Test
	void aBuildWithOneStepPlacedAlsoGetsTheNotice() {
		assertTrue(TableSession.needsTemplateGoneNotice(true, true, false, 1));
	}

	@Test
	void noNoticeWhileTheFarmFileIsThere() {
		assertFalse(TableSession.needsTemplateGoneNotice(true, false, false, 5));
	}

	@Test
	void noNoticeWhenNothingIsTakenApart() {
		assertFalse(TableSession.needsTemplateGoneNotice(false, true, false, 5));
	}

	@Test
	void noNoticeForADoneBuildOrANotStartedOne() {
		assertFalse(TableSession.needsTemplateGoneNotice(true, true, true, 5));
		assertFalse(TableSession.needsTemplateGoneNotice(true, true, false, 0));
	}
}
