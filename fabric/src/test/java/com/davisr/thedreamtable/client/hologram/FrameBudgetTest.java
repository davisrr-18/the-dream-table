package com.davisr.thedreamtable.client.hologram;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameBudgetTest {
	@Test
	void isSpentOnceTheAllowanceHasPassed() {
		AtomicLong now = new AtomicLong(1_000);
		FrameBudget budget = new FrameBudget(500, now::get);
		budget.beginFrame();

		assertFalse(budget.spent());
		now.addAndGet(499);
		assertFalse(budget.spent());
		now.addAndGet(1);
		assertTrue(budget.spent());
	}

	@Test
	void eachFrameStartsAFreshAllowanceAndCountsUp() {
		AtomicLong now = new AtomicLong(0);
		FrameBudget budget = new FrameBudget(100, now::get);

		assertEquals(1, budget.beginFrame());
		now.addAndGet(250);
		assertTrue(budget.spent());

		assertEquals(2, budget.beginFrame());
		assertFalse(budget.spent());
		assertEquals(2, budget.frame());
	}

	/** The way builders use it: a slice first, then the check, so work always advances even with no time left. */
	@Test
	void aSliceThenCheckLoopFinishesInSeveralFramesWithoutStalling() {
		AtomicLong now = new AtomicLong(0);
		FrameBudget budget = new FrameBudget(10, now::get);
		int remaining = 100;
		int frames = 0;
		while (remaining > 0) {
			budget.beginFrame();
			frames++;
			do {
				remaining -= 5;
				now.addAndGet(4);
			} while (remaining > 0 && !budget.spent());
		}

		assertEquals(0, remaining);
		assertTrue(frames > 1 && frames < 100, "spread over " + frames + " frames");
	}
}
