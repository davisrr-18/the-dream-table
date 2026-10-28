package com.davisr.thedreamtable.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshCoalescerTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("10000000-0000-0000-0000-000000000002");

	@Test
	void theFirstRequestGoesOutAtOnce() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 100));
		assertEquals(List.of(), coalescer.flush(100));
	}

	@Test
	void requestsInsideTheIntervalMergeIntoOneLaterSend() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 100));
		for (long tick = 101; tick < 110; tick++) {
			assertFalse(coalescer.request(ALICE, tick));
			assertEquals(List.of(), coalescer.flush(tick));
		}
		assertEquals(List.of(ALICE), coalescer.flush(110));
		// Delivered once: nothing is left to send, and the next window starts from this send.
		assertEquals(List.of(), coalescer.flush(111));
		assertFalse(coalescer.request(ALICE, 115));
		assertEquals(List.of(ALICE), coalescer.flush(120));
	}

	@Test
	void aRefreshIsNeverLostEvenWhenNothingAsksAgain() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 0));
		assertFalse(coalescer.request(ALICE, 3));
		assertEquals(List.of(ALICE), coalescer.flush(10_000));
	}

	@Test
	void aFloodCostsAtMostOneSendPerIntervalForThatPlayer() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		int sends = 0;
		for (long tick = 0; tick < 100; tick++) {
			for (int burst = 0; burst < 50; burst++) {
				if (coalescer.request(ALICE, tick)) {
					sends++;
				}
			}
			sends += coalescer.flush(tick).size();
		}
		assertEquals(10, sends);
	}

	@Test
	void playersAreIndependentSoOneCannotDelayAnother() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 100));
		assertFalse(coalescer.request(ALICE, 101));
		assertTrue(coalescer.request(BOB, 101));
		assertEquals(List.of(ALICE), coalescer.flush(110));
		assertEquals(List.of(), coalescer.flush(110));
	}

	@Test
	void anImmediateSendSupersedesAPendingOne() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 100));
		assertFalse(coalescer.request(ALICE, 105));
		assertTrue(coalescer.request(ALICE, 110));
		assertEquals(List.of(), coalescer.flush(111));
		assertEquals(List.of(), coalescer.flush(130));
	}

	@Test
	void forgettingAPlayerDropsTheirPendingRefreshAndHistory() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 100));
		assertFalse(coalescer.request(ALICE, 101));
		coalescer.forget(ALICE);
		assertEquals(List.of(), coalescer.flush(200));
		assertTrue(coalescer.request(ALICE, 102));
	}

	@Test
	void aTickCounterThatWentBackwardsDoesNotDelayTheRefresh() {
		RefreshCoalescer coalescer = new RefreshCoalescer(10);
		assertTrue(coalescer.request(ALICE, 5000));
		assertTrue(coalescer.request(ALICE, 20));
	}
}
