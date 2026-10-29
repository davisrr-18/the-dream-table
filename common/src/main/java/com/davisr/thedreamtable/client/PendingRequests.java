package com.davisr.thedreamtable.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which requests are in flight so a screen that asks every frame sends each one once, and forgets a request
 * that went unanswered for {@link #RETRY_AFTER_MS}: the server may have dropped it (its request limit), and the asker
 * would otherwise wait for an answer that never comes. Time is passed in so the timing can be tested.
 */
final class PendingRequests {
	static final long RETRY_AFTER_MS = 2000L;

	private final Map<String, Long> sentAt = new HashMap<>();

	/** True when the request should be sent now: it is new, or its last attempt is older than the retry delay. */
	synchronized boolean shouldSend(String id, long nowMs) {
		Long last = sentAt.get(id);
		if (last != null && nowMs - last < RETRY_AFTER_MS) {
			return false;
		}
		sentAt.put(id, nowMs);
		return true;
	}

	synchronized void clear() {
		sentAt.clear();
	}
}
