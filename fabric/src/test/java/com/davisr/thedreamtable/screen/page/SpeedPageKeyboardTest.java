package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.screen.ui.Adjustment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedPageKeyboardTest {
	@Test
	void arrowsStepOnePercentAndStayInsideTheRange() {
		assertEquals(51, SpeedPage.stepped(50, Adjustment.INCREASE));
		assertEquals(49, SpeedPage.stepped(50, Adjustment.DECREASE));
		assertEquals(BuildPace.MIN_PERCENT, SpeedPage.stepped(BuildPace.MIN_PERCENT, Adjustment.DECREASE));
		assertEquals(BuildPace.MAX_PERCENT, SpeedPage.stepped(BuildPace.MAX_PERCENT, Adjustment.INCREASE));
	}

	@Test
	void homeAndEndJumpToTheEnds() {
		assertEquals(BuildPace.MIN_PERCENT, SpeedPage.stepped(50, Adjustment.MINIMUM));
		assertEquals(BuildPace.MAX_PERCENT, SpeedPage.stepped(50, Adjustment.MAXIMUM));
	}

	@Test
	void adjustmentAppliesTheSameRules() {
		assertEquals(7, Adjustment.INCREASE.apply(5, 2, 0, 100));
		assertEquals(0, Adjustment.DECREASE.apply(1, 2, 0, 100));
		assertEquals(100, Adjustment.INCREASE.apply(99, 5, 0, 100));
		assertEquals(0, Adjustment.MINIMUM.apply(42, 1, 0, 100));
		assertEquals(100, Adjustment.MAXIMUM.apply(42, 1, 0, 100));
	}
}
