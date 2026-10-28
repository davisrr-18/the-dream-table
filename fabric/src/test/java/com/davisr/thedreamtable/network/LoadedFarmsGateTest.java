package com.davisr.thedreamtable.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedFarmsGateTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Test
	void aPlainListReadFollowsItsBudget() {
		assertEquals(LoadedFarmsGate.Route.LIST, LoadedFarmsGate.route(false, true));
		assertEquals(LoadedFarmsGate.Route.DROP, LoadedFarmsGate.route(false, false));
	}

	@Test
	void aToggleIsBudgetedTooAndAnswersBusyInsteadOfScanning() {
		assertEquals(LoadedFarmsGate.Route.TOGGLE, LoadedFarmsGate.route(true, true));
		assertEquals(LoadedFarmsGate.Route.BUSY, LoadedFarmsGate.route(true, false));
	}

	@Test
	void aToggleFloodReachesTheHandlerOnlyWithinItsBudgetAndNeverFallsThroughToTheList() {
		RequestThrottle toggles = new RequestThrottle(6, 20);
		int handled = 0;
		int busy = 0;
		for (int request = 0; request < 100; request++) {
			switch (LoadedFarmsGate.route(true, toggles.allow(ALICE, 500))) {
				case TOGGLE -> handled++;
				case BUSY -> busy++;
				default -> throw new AssertionError("A toggle must never be routed as a list read");
			}
		}
		assertEquals(6, handled);
		assertEquals(94, busy);
	}

	@Test
	void theDeniedToggleScanIsBoundedByTheSharedRefreshBudget() {
		// Each accepted toggle, granted or denied by authorization, asks for a refresh through the coalescer.
		RequestThrottle toggles = new RequestThrottle(6, 20);
		RefreshCoalescer refreshes = new RefreshCoalescer();
		int scans = 0;
		for (long tick = 0; tick < 20; tick++) {
			for (int request = 0; request < 20; request++) {
				if (LoadedFarmsGate.route(true, toggles.allow(ALICE, tick)) == LoadedFarmsGate.Route.TOGGLE && refreshes.request(ALICE, tick)) {
					scans++;
				}
			}
			scans += refreshes.flush(tick).size();
		}
		assertTrue(scans <= 2, "Scans in one second: " + scans);
		assertFalse(scans == 0);
	}
}
