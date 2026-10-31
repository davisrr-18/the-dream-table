package com.davisr.thedreamtable.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pointer is stored as normalized device coordinates, with y pointing up and the window's aspect ratio. */
class MapModePointerTest {
	private static final double DELTA = 1e-9;

	@Test
	void centreIsZeroAndCornersAreTheNdcEdges() {
		MapMode.pointer(400, 300, 800, 600);
		assertEquals(0.0, MapMode.ndcX(), DELTA);
		assertEquals(0.0, MapMode.ndcY(), DELTA);

		MapMode.pointer(0, 0, 800, 600);
		assertEquals(-1.0, MapMode.ndcX(), DELTA);
		assertEquals(1.0, MapMode.ndcY(), DELTA);

		MapMode.pointer(800, 600, 800, 600);
		assertEquals(1.0, MapMode.ndcX(), DELTA);
		assertEquals(-1.0, MapMode.ndcY(), DELTA);
	}

	@Test
	void aspectIsWidthOverHeight() {
		MapMode.pointer(10, 10, 800, 600);
		assertEquals(800.0 / 600.0, MapMode.aspect(), DELTA);
	}

	@Test
	void emptyWindowKeepsThePreviousPointer() {
		MapMode.pointer(0, 0, 800, 600);
		MapMode.pointer(5, 5, 0, 0);
		assertEquals(-1.0, MapMode.ndcX(), DELTA);
		assertEquals(1.0, MapMode.ndcY(), DELTA);
	}
}
