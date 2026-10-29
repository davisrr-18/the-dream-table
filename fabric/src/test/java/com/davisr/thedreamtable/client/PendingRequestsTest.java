package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A schematic request the server dropped must be asked again, but not every frame. */
class PendingRequestsTest {
	@Test
	void aRequestIsSentOnceWhileItIsFresh() {
		PendingRequests pending = new PendingRequests();

		assertTrue(pending.shouldSend("a", 0));
		assertFalse(pending.shouldSend("a", 1));
		assertFalse(pending.shouldSend("a", PendingRequests.RETRY_AFTER_MS - 1));
	}

	@Test
	void anUnansweredRequestIsAskedAgainAfterTheDelayAndThenWaitsAgain() {
		PendingRequests pending = new PendingRequests();
		assertTrue(pending.shouldSend("a", 1000));

		assertTrue(pending.shouldSend("a", 1000 + PendingRequests.RETRY_AFTER_MS));
		assertFalse(pending.shouldSend("a", 1000 + PendingRequests.RETRY_AFTER_MS + 1));
		assertTrue(pending.shouldSend("a", 1000 + 2 * PendingRequests.RETRY_AFTER_MS));
	}

	@Test
	void requestsAreTrackedPerFarm() {
		PendingRequests pending = new PendingRequests();

		assertTrue(pending.shouldSend("a", 0));
		assertTrue(pending.shouldSend("b", 1));
		assertFalse(pending.shouldSend("a", 2));
	}

	@Test
	void clearingForgetsEveryRequest() {
		PendingRequests pending = new PendingRequests();
		pending.shouldSend("a", 0);

		pending.clear();

		assertTrue(pending.shouldSend("a", 1));
	}
}
