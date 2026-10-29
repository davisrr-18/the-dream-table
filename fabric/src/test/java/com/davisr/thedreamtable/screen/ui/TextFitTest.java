package com.davisr.thedreamtable.screen.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tooltip width math and the marquee that keeps long labels readable. */
class TextFitTest {
	@Test
	void aTooltipNeverWrapsWiderThanHalfTheWindowOrTheCap() {
		for (int width = 100; width <= 4000; width += 7) {
			int max = Tooltips.maxWidth(width);
			assertTrue(max >= Tooltips.MIN_WIDTH && max <= Tooltips.MAX_WIDTH, "width " + width);
			if (width >= 2 * (Tooltips.MIN_WIDTH + 12)) {
				// Flipped to the left of the mouse, the text box and its 12 px gap still fit in the window.
				assertTrue(max + 12 <= width / 2, "width " + width);
			}
		}
		assertEquals(Tooltips.MAX_WIDTH, Tooltips.maxWidth(854));
		assertEquals(130, Tooltips.maxWidth(284));
	}

	@Test
	void textThatFitsDoesNotMove() {
		assertEquals(0, Marquee.offset(100, 100, 12345));
		assertEquals(0, Marquee.offset(40, 100, 12345));
	}

	@Test
	void longTextSweepsItsOverflowAndNothingMore() {
		int textWidth = 180;
		int box = 100;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (long millis = 0; millis < 60_000; millis += 50) {
			int offset = Marquee.offset(textWidth, box, millis);
			min = Math.min(min, offset);
			max = Math.max(max, offset);
		}
		assertTrue(min <= 1, "starts at the left edge");
		assertTrue(max >= textWidth - box - 1 && max <= textWidth - box, "the end of the text reaches the end of the box");
	}
}
