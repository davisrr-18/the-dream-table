package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsePressTest {
	@Test
	void anEmptyHandAtAirIsSeenOnlyByTheTickAndOnlyOnce() {
		UsePress use = new UsePress();
		assertTrue(use.onTick(true), "no use event fired, the key going down is the click");
		assertFalse(use.onTick(true), "still held");
		assertFalse(use.onTick(true));
		assertFalse(use.onTick(false));
		assertTrue(use.onTick(true), "the next press is a new click");
	}

	@Test
	void aClickThatFiredAUseEventIsNotRecordedAgainByTheTick() {
		UsePress use = new UsePress();
		assertTrue(use.onUseEvent());
		assertFalse(use.onTick(true), "the same press, seen by the tick too");
		assertFalse(use.onTick(true));
	}

	@Test
	void aTapShorterThanATickThatFiredAnEventCountsOnceAndTheNextTapIsNew() {
		UsePress use = new UsePress();
		assertTrue(use.onUseEvent());
		assertFalse(use.onTick(false));
		assertTrue(use.onUseEvent());
		assertFalse(use.onTick(false));
	}

	@Test
	void aHeldKeysRepeatedUseEventsAreNotNewClicks() {
		UsePress use = new UsePress();
		assertTrue(use.onUseEvent());
		use.onTick(true);
		assertFalse(use.onUseEvent(), "the game repeats the use every few ticks while the key is held");
		use.onTick(true);
		assertFalse(use.onUseEvent());
		use.onTick(false);
		assertTrue(use.onUseEvent());
	}

	@Test
	void aTickPressAlsoStopsAnEventOfTheSamePress() {
		UsePress use = new UsePress();
		assertTrue(use.onTick(true));
		assertFalse(use.onUseEvent());
	}
}
