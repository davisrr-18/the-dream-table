package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackedFarmHudTest {
	@Test
	void directionUsesTheDominantHorizontalAxis() {
		assertEquals("E", TrackedFarmHud.direction(8, 3));
		assertEquals("W", TrackedFarmHud.direction(-8, 3));
		assertEquals("S", TrackedFarmHud.direction(3, 8));
		assertEquals("N", TrackedFarmHud.direction(3, -8));
	}
}
