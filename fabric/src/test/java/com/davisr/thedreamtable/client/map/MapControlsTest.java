package com.davisr.thedreamtable.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The controls panel flows whole items into rows. */
class MapControlsTest {
	@Test
	void itemsThatFitShareOneRow() {
		assertEquals(List.of(List.of(0, 1, 2)), MapControls.rows(new int[] {30, 30, 30}, 100, 5));
	}

	@Test
	void anItemThatWouldOverflowStartsTheNextRowWhole() {
		assertEquals(List.of(List.of(0, 1), List.of(2)), MapControls.rows(new int[] {40, 40, 40}, 100, 10));
	}

	@Test
	void anItemWiderThanTheSpaceGetsARowOfItsOwn() {
		assertEquals(List.of(List.of(0), List.of(1), List.of(2)), MapControls.rows(new int[] {20, 200, 20}, 100, 5));
	}

	@Test
	void noItemsGiveNoRows() {
		assertEquals(List.of(), MapControls.rows(new int[0], 100, 5));
	}

	@Test
	void aRowIsItsItemsAndTheGapsBetweenThem() {
		assertEquals(95, MapControls.rowWidth(List.of(0, 1, 2), new int[] {25, 25, 25}, 10));
	}
}
