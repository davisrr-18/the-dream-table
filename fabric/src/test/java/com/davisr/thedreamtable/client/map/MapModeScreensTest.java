package com.davisr.thedreamtable.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapModeScreensTest {
	@Test
	void theMapsOwnScreenIsAlwaysOurs() {
		assertTrue(MapMode.ownsScreen(true, false, false));
		assertTrue(MapMode.ownsScreen(true, false, true));
	}

	@Test
	void theNameStepIsOursOnlyInTheCaptureVariant() {
		assertTrue(MapMode.ownsScreen(false, true, true));
		assertFalse(MapMode.ownsScreen(false, true, false));
	}

	@Test
	void anyOtherScreenIsNotOursSoItIsNeverClosedWithTheMode() {
		assertFalse(MapMode.ownsScreen(false, false, true), "a server container or a kick reason over the capture map");
		assertFalse(MapMode.ownsScreen(false, false, false));
	}
}
