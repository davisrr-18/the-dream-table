package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmGuardTest {
	@Test
	void aPressInTheGameCounts() {
		ConfirmGuard guard = new ConfirmGuard();
		assertTrue(guard.accept(1, false, true));
		assertFalse(guard.accept(0, false, true));
		assertFalse(guard.accept(0, false, false));
		assertTrue(guard.accept(1, false, true), "the next press is new");
	}

	@Test
	void theEnterThatClosedAScreenIsNotAPressEvenWhenItsRepeatsKeepArriving() {
		ConfirmGuard guard = new ConfirmGuard();
		assertFalse(guard.accept(0, true, true), "Enter is down while the name screen is open");
		assertFalse(guard.accept(1, true, true), "the first tick after the screen: still counted as screen time");
		for (int tick = 0; tick < 30; tick++) {
			assertFalse(guard.accept(1, false, true), "OS repeats of the same hold, tick " + tick);
		}
		assertFalse(guard.accept(0, false, false), "released");
		assertTrue(guard.accept(1, false, true), "a new press after the release counts");
	}

	@Test
	void aKeyTappedAndReleasedBeforeTheScreenClosedCountsNothing() {
		ConfirmGuard guard = new ConfirmGuard();
		assertFalse(guard.accept(0, true, false));
		assertFalse(guard.accept(1, true, false), "a press seen on the screen's own tick is the screen's");
		assertTrue(guard.accept(1, false, false), "a quick tap in the game after it is a press, even if released by the tick");
	}

	@Test
	void screenTimeAloneIsNotABlockWhenTheKeyIsUp() {
		ConfirmGuard guard = new ConfirmGuard();
		assertFalse(guard.accept(0, true, false));
		assertTrue(guard.accept(2, false, true));
	}
}
