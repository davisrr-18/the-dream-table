package com.davisr.thedreamtable.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan-heavy requests allow only a few a second, and a repeat of the same question is answered without a new scan. */
class ScanBudgetsTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("10000000-0000-0000-0000-000000000002");

	@Test
	void theScanHeavyBudgetsAreAFewPerSecond() {
		assertTrue(CheckPlacementPayload.BUDGET <= 4 && CheckPlacementPayload.BUDGET >= 3);
		assertTrue(FarmDetailsPayload.BUDGET <= 4 && FarmDetailsPayload.BUDGET >= 3);
		assertTrue(LoadedFarmsPayload.LIST_BUDGET <= 4 && LoadedFarmsPayload.LIST_BUDGET >= 3);
	}

	@Test
	void aSpammedPlacementCheckGetsOnlyItsBudgetPerSecond() {
		RequestThrottle throttle = new RequestThrottle(CheckPlacementPayload.BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);
		int accepted = 0;
		for (int tick = 0; tick < 20; tick++) {
			for (int packet = 0; packet < 5; packet++) {
				accepted += throttle.allow(ALICE, 1000 + tick) ? 1 : 0;
			}
		}
		assertEquals(CheckPlacementPayload.BUDGET, accepted);
	}

	@Test
	void theClientsOwnRetryRatesFitTheBudgets() {
		RequestThrottle list = new RequestThrottle(LoadedFarmsPayload.LIST_BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);
		RequestThrottle placement = new RequestThrottle(CheckPlacementPayload.BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);
		for (int second = 0; second < 30; second++) {
			// The My Farms page asks again every second until the list arrives.
			assertTrue(list.allow(ALICE, second * 20L));
		}
		for (int tick = 0; tick < 400; tick += 40) {
			// The hologram rechecks every two seconds.
			assertTrue(placement.allow(ALICE, tick));
		}
		// Anchoring, then turning and mirroring quickly, still fits in one second.
		assertTrue(placement.allow(ALICE, 1000));
		assertTrue(placement.allow(ALICE, 1002));
		assertTrue(placement.allow(ALICE, 1004));
		assertTrue(placement.allow(ALICE, 1006));
		assertFalse(placement.allow(ALICE, 1008));
	}

	@Test
	void theSameQuestionWithinTheTimeToLiveGetsTheStoredAnswer() {
		RecentResults<String, Object> recent = new RecentResults<>(20);
		Object answer = new Object();
		recent.put(ALICE, "question", answer, 100);

		assertSame(answer, recent.get(ALICE, "question", 100));
		assertSame(answer, recent.get(ALICE, "question", 119));
		assertNull(recent.get(ALICE, "question", 120));
	}

	@Test
	void aDifferentQuestionOrPlayerIsNeverAnsweredFromTheStore() {
		RecentResults<String, Object> recent = new RecentResults<>(20);
		recent.put(ALICE, "question", new Object(), 100);

		assertNull(recent.get(ALICE, "another", 101));
		assertNull(recent.get(BOB, "question", 101));
	}

	@Test
	void theNewestAnswerReplacesTheOldOneAndForgettingDropsIt() {
		RecentResults<String, Object> recent = new RecentResults<>(20);
		Object first = new Object();
		Object second = new Object();
		recent.put(ALICE, "question", first, 100);
		recent.put(ALICE, "question", second, 105);

		assertSame(second, recent.get(ALICE, "question", 106));
		recent.forget(ALICE);
		assertNull(recent.get(ALICE, "question", 106));
	}

	@Test
	void aTickCounterThatWentBackwardsMakesTheAnswerStale() {
		RecentResults<String, Object> recent = new RecentResults<>(20);
		recent.put(ALICE, "question", new Object(), 5000);
		assertNull(recent.get(ALICE, "question", 10));
	}
}
