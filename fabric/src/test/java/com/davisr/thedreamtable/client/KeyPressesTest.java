package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyPressesTest {
	@Test
	void theFirstRescanIsAlwaysDueEvenAtAHugeOrNegativeClock() {
		assertTrue(KeyPresses.rescanDue(false, 1000, Long.MIN_VALUE, 20), "the first scan must not depend on a sentinel time");
		assertTrue(KeyPresses.rescanDue(false, 0, 0, 20));
		assertTrue(KeyPresses.rescanDue(false, Long.MAX_VALUE, 0, 20));
	}

	@Test
	void laterRescansWaitTheIntervalAndRestartWhenTheClockGoesBack() {
		assertFalse(KeyPresses.rescanDue(true, 1010, 1000, 20));
		assertTrue(KeyPresses.rescanDue(true, 1020, 1000, 20));
		assertTrue(KeyPresses.rescanDue(true, 5, 1000, 20), "a new world's clock starts again");
	}

	@Test
	void heldCountsTicksDownAndResetsOnRelease() {
		assertEquals(1, KeyPresses.held(0, true));
		assertEquals(4, KeyPresses.held(3, true));
		assertEquals(0, KeyPresses.held(7, false));
	}

	@Test
	void aKeyActsOnItsFirstTickAndRepeatsOnlyWhenItRepeats() {
		assertTrue(KeyPresses.acts(1, false, 10, false));
		assertFalse(KeyPresses.acts(2, false, 10, false));
		assertFalse(KeyPresses.acts(12, false, 10, false), "Enter never repeats");
		assertFalse(KeyPresses.acts(9, true, 10, false));
		assertTrue(KeyPresses.acts(10, true, 10, false));
		assertFalse(KeyPresses.acts(11, true, 10, false));
		assertTrue(KeyPresses.acts(12, true, 10, false));
		assertFalse(KeyPresses.acts(0, true, 10, false));
	}

	@Test
	void aKeyThatClosedAScreenDoesNotActInTheGameOnTheNextTick() {
		assertFalse(KeyPresses.acts(1, false, 10, true), "the Enter that saved the name or sent the chat line");
		assertTrue(KeyPresses.acts(1, false, 10, false));
	}
}
