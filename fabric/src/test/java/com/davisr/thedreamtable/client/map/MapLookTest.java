package com.davisr.thedreamtable.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mouse look's scaling, as the game's own look computes it. */
class MapLookTest {
	private static final double DELTA = 1e-9;

	@Test
	void theDefaultSensitivityGivesFactorOne() {
		assertEquals(1.0, MapLook.factor(0.5), DELTA);
	}

	@Test
	void theFullSensitivityGivesTheCubeOfEightTenths() {
		assertEquals(0.8 * 0.8 * 0.8 * 8.0, MapLook.factor(1.0), DELTA);
	}

	@Test
	void aPixelTurnsFifteenHundredthsOfADegreeAtFactorOne() {
		assertEquals(15.0, MapLook.degrees(100, 1.0), DELTA);
		assertEquals(-15.0, MapLook.degrees(-100, 1.0), DELTA);
	}

	@Test
	void aLargePointerJumpIsNotATurn() {
		assertTrue(MapLook.plausible(200));
		assertTrue(MapLook.plausible(-200));
		assertFalse(MapLook.plausible(200.5));
	}
}
