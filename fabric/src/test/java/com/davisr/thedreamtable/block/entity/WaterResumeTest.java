package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a build stopped for want of water is retried without the player: only when begin would accept it. */
class WaterResumeTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** A stopped water build, on a second, at the table it started from, with its farm file unchanged. */
	private static boolean retry(boolean stopped, boolean waterIssue, boolean secondTick, boolean movedBuild, boolean hasTemplate, boolean signatureMatches) {
		return TableSession.retriesForWater(stopped, waterIssue, secondTick, movedBuild, hasTemplate, signatureMatches);
	}

	@Test
	void aStoppedWaterBuildIsRetriedOnASecond() {
		assertTrue(retry(true, true, true, false, true, true));
	}

	@Test
	void aMovedBuildWaitsForContinueEvenBesideWater() {
		assertFalse(retry(true, true, true, true, true, true));
	}

	@Test
	void aBuildWithAMissingOrChangedFarmFileIsNotRetried() {
		assertFalse(retry(true, true, true, false, false, false));
		assertFalse(retry(true, true, true, false, true, false));
	}

	@Test
	void onlyAStoppedWaterIssueOnASecondIsRetried() {
		assertFalse(retry(false, true, true, false, true, true));
		assertFalse(retry(true, false, true, false, true, true));
		assertFalse(retry(true, true, false, false, true, true));
	}
}
