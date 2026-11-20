package com.davisr.thedreamtable.screen.ui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hidden or partly scrolled-out text box must not take a click that lands on its old place. */
class ClickAreaTest {
	private static final UiRect BOX = new UiRect(0, 0, 10, 10);

	@Test
	void anUnusableBoxNeverTakesAClick() {
		assertFalse(ClickArea.hits(false, BOX, 5, 5));
	}

	@Test
	void ausableBoxTakesClicksInsideItsBoundsOnly() {
		assertTrue(ClickArea.hits(true, BOX, 0, 0));
		assertTrue(ClickArea.hits(true, BOX, 9.9, 9.9));
		assertFalse(ClickArea.hits(true, BOX, 10, 5));
		assertFalse(ClickArea.hits(true, BOX, 5, 10));
		assertFalse(ClickArea.hits(true, BOX, -1, 5));
	}

	@Test
	void aZeroSizeBoxTakesNoClick() {
		assertFalse(ClickArea.hits(true, new UiRect(0, 0, 0, 0), 0, 0));
	}
}
