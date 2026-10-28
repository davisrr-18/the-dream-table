package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.SlotGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotGeometryTest {
	private static final UiRect VIEWPORT = new UiRect(20, 40, 168, 100);

	@Test
	void acceptsOnlySlotsWhollyContainedByTheirPageViewport() {
		assertTrue(SlotGeometry.interactive(true, VIEWPORT, new UiRect(20, 40, 18, 18)));
		assertTrue(SlotGeometry.interactive(true, VIEWPORT, new UiRect(170, 122, 18, 18)));
		assertFalse(SlotGeometry.interactive(true, VIEWPORT, new UiRect(19, 40, 18, 18)));
		assertFalse(SlotGeometry.interactive(true, VIEWPORT, new UiRect(20, 39, 18, 18)));
		assertFalse(SlotGeometry.interactive(true, VIEWPORT, new UiRect(171, 122, 18, 18)));
		assertFalse(SlotGeometry.interactive(true, VIEWPORT, new UiRect(170, 123, 18, 18)));
	}

	@Test
	void rejectsSlotsWhenThePageDoesNotAllowThem() {
		assertFalse(SlotGeometry.interactive(false, VIEWPORT, new UiRect(20, 40, 18, 18)));
	}
}
