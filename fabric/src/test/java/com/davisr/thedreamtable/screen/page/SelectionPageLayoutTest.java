package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Build Queue and Clear Queue buttons are standard full-width buttons, stacked above the category cards. */
class SelectionPageLayoutTest {
	private static Viewport view(int width) {
		Viewport view = new Viewport();
		view.layout(ScreenGeometry.calculate(width, 240, 0, false), 0, 300);
		return view;
	}

	@Test
	void theQueueButtonsAreStandardButtonsStackedAboveTheCards() {
		for (int width : new int[] {416, 420}) {
			Viewport view = view(width);
			PageContext ui = new PageContext(null, view, null, null);
			int x = view.collectionX();
			int start = SelectionPage.startY(view);
			int clear = SelectionPage.clearY(view);

			// The hit area is the standard button's: the whole collection width, as the Back button has.
			assertTrue(ui.buttonHit(x + 1, start + 1, x, start));
			assertTrue(ui.buttonHit(x + view.flowWidth() - 1, clear + 1, x, clear));
			assertFalse(ui.buttonHit(x + view.flowWidth(), start + 1, x, start), "not wider than the collection");
			assertFalse(ui.buttonHit(x + 1, start + BUTTON_HEIGHT + 1, x, start), "the gap between the rows hits neither");
			assertEquals(start + BUTTON_HEIGHT + BUTTON_GAP, clear);
			assertTrue(SelectionPage.cardY(view, 0, true) >= clear + BUTTON_HEIGHT, "the cards start under the second button");
			assertTrue(SelectionPage.cardY(view, 0, true) > SelectionPage.cardY(view, 0, false));
		}
	}

	@Test
	void withoutAQueueTheCardsAndHeightAreUnchanged() {
		Viewport view = view(416);
		assertEquals(view.gridCardY(3), SelectionPage.cardY(view, 3, false));
		assertEquals(view.gridContentHeight(6), SelectionPage.contentHeight(view, 6, false));
		assertEquals(view.gridContentHeight(6) + SelectionPage.QUEUE_ROWS, SelectionPage.contentHeight(view, 6, true));
		assertEquals(SelectionPage.QUEUE_ROWS, SelectionPage.cardY(view, 3, true) - view.gridCardY(3));
	}
}
