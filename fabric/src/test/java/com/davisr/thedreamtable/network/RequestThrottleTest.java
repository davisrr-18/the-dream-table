package com.davisr.thedreamtable.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestThrottleTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("10000000-0000-0000-0000-000000000002");

	@Test
	void aBurstWithinTheBudgetAlwaysPasses() {
		RequestThrottle throttle = new RequestThrottle(3, 20);
		assertTrue(throttle.allow(ALICE, 100));
		assertTrue(throttle.allow(ALICE, 100));
		assertTrue(throttle.allow(ALICE, 101));
		assertFalse(throttle.allow(ALICE, 102));
	}

	@Test
	void theDefaultPassesTenClicksInOneSecond() {
		RequestThrottle throttle = new RequestThrottle();
		for (int click = 0; click < RequestThrottle.DEFAULT_BUDGET; click++) {
			assertTrue(throttle.allow(ALICE, 50 + click));
		}
		assertFalse(throttle.allow(ALICE, 60));
	}

	@Test
	void theBudgetComesBackAsTheWindowSlides() {
		RequestThrottle throttle = new RequestThrottle(2, 10);
		assertTrue(throttle.allow(ALICE, 100));
		assertTrue(throttle.allow(ALICE, 105));
		assertFalse(throttle.allow(ALICE, 109));
		assertTrue(throttle.allow(ALICE, 110));
		assertFalse(throttle.allow(ALICE, 111));
		assertTrue(throttle.allow(ALICE, 115));
	}

	@Test
	void aDroppedRequestDoesNotUseTheBudget() {
		RequestThrottle throttle = new RequestThrottle(1, 10);
		assertTrue(throttle.allow(ALICE, 10));
		assertFalse(throttle.allow(ALICE, 12));
		assertFalse(throttle.allow(ALICE, 15));
		assertTrue(throttle.allow(ALICE, 20));
	}

	@Test
	void playersAreThrottledSeparately() {
		RequestThrottle throttle = new RequestThrottle(1, 10);
		assertTrue(throttle.allow(ALICE, 10));
		assertTrue(throttle.allow(BOB, 10));
		assertFalse(throttle.allow(ALICE, 11));
	}

	@Test
	void forgettingAPlayerLetsTheNextRequestThrough() {
		RequestThrottle throttle = new RequestThrottle(1, 10);
		assertTrue(throttle.allow(ALICE, 10));
		throttle.forget(ALICE);
		assertTrue(throttle.allow(ALICE, 11));
	}

	@Test
	void aTickCounterThatWentBackwardsDoesNotLockThePlayerOut() {
		RequestThrottle throttle = new RequestThrottle(1, 10);
		assertTrue(throttle.allow(ALICE, 1000));
		assertTrue(throttle.allow(ALICE, 3));
	}
}
