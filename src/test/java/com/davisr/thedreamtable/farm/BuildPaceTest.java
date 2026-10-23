package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPaceTest {
	@Test
	void offeredDurationsAreKept() {
		for (int minutes : BuildPace.MINUTES) {
			assertEquals(BuildPace.ofMinutes(minutes), BuildPace.valid(BuildPace.ofMinutes(minutes)));
		}
	}

	@Test
	void oldBlocksPerSecondAndUnknownPacesUseTheDefault() {
		for (int old : new int[]{1, 2, 4, 8, 0, 3, -2, 999}) {
			assertEquals(BuildPace.DEFAULT, BuildPace.valid(old));
		}
	}

	@Test
	void durationSpreadsTheFarmOverTheTime() {
		assertEquals(9, BuildPace.blocksPerSecond(BuildPace.ofMinutes(5), 2505));
		assertEquals(5, BuildPace.blocksPerSecond(BuildPace.ofMinutes(10), 2505));
		assertEquals(42, BuildPace.blocksPerSecond(BuildPace.ofMinutes(1), 2505));
		for (int minutes : BuildPace.MINUTES) {
			assertTrue(BuildPace.secondsLeft(BuildPace.ofMinutes(minutes), 2505, 0) <= minutes * 60);
		}
	}

	@Test
	void durationIsCappedForHugeFarms() {
		assertEquals(BuildPace.MAX_RATE, BuildPace.blocksPerSecond(BuildPace.ofMinutes(1), 16384));
	}

	@Test
	void smallFarmsNeverGoBelowOneBlockPerSecond() {
		assertEquals(1, BuildPace.blocksPerSecond(BuildPace.ofMinutes(10), 40));
		assertEquals(1, BuildPace.blocksPerSecond(BuildPace.ofMinutes(10), 0));
	}

	@Test
	void timeLeftCountsOnlyRemainingSteps() {
		int fiveMinutes = BuildPace.ofMinutes(5);
		assertEquals(279, BuildPace.secondsLeft(fiveMinutes, 2505, 0));
		assertEquals(57, BuildPace.secondsLeft(fiveMinutes, 2505, 2000));
		assertEquals(0, BuildPace.secondsLeft(fiveMinutes, 2505, 2505));
		assertEquals(0, BuildPace.secondsLeft(fiveMinutes, 2505, 3000));
	}
}
