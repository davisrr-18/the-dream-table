package com.davisr.dreamtablepoc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PocRulesTest {
	@Test
	void allowsLoadedCounterBlockWithinReach() {
		assertTrue(PocRules.canIncrement(true, true, true, false, true));
	}

	@Test
	void rejectsOutOfReachRequests() {
		assertFalse(PocRules.canIncrement(true, true, true, false, false));
	}

	@Test
	void rejectsRequestsWithoutOpenMenuForTarget() {
		assertFalse(PocRules.canIncrement(true, true, false, false, true));
	}

	@Test
	void rejectsSpectators() {
		assertFalse(PocRules.canIncrement(true, true, true, true, true));
	}

	@Test
	void rejectsWrongBlockOrUnloadedChunk() {
		assertFalse(PocRules.canIncrement(true, false, true, false, true));
		assertFalse(PocRules.canIncrement(false, true, true, false, true));
	}
}
