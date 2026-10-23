package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildPaceTest {
	@Test
	void levelsAreKept() {
		for (int level : BuildPace.LEVELS) {
			assertEquals(level, BuildPace.valid(level));
		}
	}

	@Test
	void oldDurationsMapToTheClosestLevel() {
		assertEquals(BuildPace.SLOW, BuildPace.valid(-10));
		assertEquals(BuildPace.NORMAL, BuildPace.valid(-5));
		assertEquals(BuildPace.FAST, BuildPace.valid(-1));
	}

	@Test
	void oldBlocksPerSecondAndUnknownPacesUseTheDefault() {
		for (int old : new int[]{1, 2, 4, 8, 0, 3, -2, 999}) {
			assertEquals(BuildPace.DEFAULT, BuildPace.valid(old));
		}
	}

	@Test
	void levelsAreSharesOfTheServerLimit() {
		assertEquals(64, BuildPace.blocksPerSecond(BuildPace.SLOW, 256));
		assertEquals(128, BuildPace.blocksPerSecond(BuildPace.NORMAL, 256));
		assertEquals(256, BuildPace.blocksPerSecond(BuildPace.FAST, 256));
		assertEquals(5, BuildPace.blocksPerSecond(BuildPace.SLOW, 20));
	}

	@Test
	void customSharesAreKeptAndClamped() {
		assertEquals(37, BuildPace.percent(BuildPace.ofPercent(37)));
		assertEquals(94, BuildPace.blocksPerSecond(BuildPace.ofPercent(37), 256));
		assertEquals(BuildPace.MIN_PERCENT, BuildPace.percent(BuildPace.ofPercent(0)));
		assertEquals(BuildPace.MAX_PERCENT, BuildPace.percent(BuildPace.ofPercent(250)));
		assertEquals(false, BuildPace.isLevel(BuildPace.ofPercent(37)));
		assertEquals(true, BuildPace.isLevel(BuildPace.ofPercent(50)));
	}

	@Test
	void rateNeverDropsBelowOne() {
		assertEquals(1, BuildPace.blocksPerSecond(BuildPace.SLOW, 1));
		assertEquals(1, BuildPace.blocksPerSecond(BuildPace.SLOW, 0));
	}

	@Test
	void serverLimitStaysInRange() {
		assertEquals(1, BuildPace.clampMax(0));
		assertEquals(1, BuildPace.clampMax(-50));
		assertEquals(BuildPace.MAX_LIMIT, BuildPace.clampMax(100000));
		assertEquals(256, BuildPace.clampMax(256));
		assertEquals(BuildPace.MAX_LIMIT, BuildPace.blocksPerSecond(BuildPace.FAST, 5000));
	}

	@Test
	void timeLeftCountsOnlyRemainingSteps() {
		assertEquals(10, BuildPace.secondsLeftAt(256, 2560, 0));
		assertEquals(4, BuildPace.secondsLeftAt(128, 2505, 2000));
		assertEquals(0, BuildPace.secondsLeftAt(256, 2560, 2560));
		assertEquals(0, BuildPace.secondsLeftAt(256, 2560, 3000));
		assertEquals(5, BuildPace.secondsLeftAt(0, 5, 0));
	}
}
