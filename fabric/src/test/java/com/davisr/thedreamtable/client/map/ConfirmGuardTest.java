package com.davisr.thedreamtable.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmGuardTest {
	@Test
	void aPressWithNoScreenBehindItCounts() {
		ConfirmGuard guard = new ConfirmGuard();
		assertTrue(guard.accept(true));
		assertTrue(guard.accept(true));
	}

	@Test
	void theEnterThatClosedTheNameScreenIsNotAPressEvenWhenItsRepeatsKeepArriving() {
		ConfirmGuard guard = new ConfirmGuard();
		guard.screenClosed(true);
		for (int repeat = 0; repeat < 30; repeat++) {
			assertFalse(guard.accept(true), "OS repeat " + repeat + " of the same hold");
		}
		assertTrue(guard.accept(false), "released: the key is free again");
		assertTrue(guard.accept(true), "a new press counts");
	}

	@Test
	void aKeyAlreadyUpWhenTheScreenClosedBlocksNothing() {
		ConfirmGuard guard = new ConfirmGuard();
		guard.screenClosed(false);
		assertTrue(guard.accept(true));
	}
}
