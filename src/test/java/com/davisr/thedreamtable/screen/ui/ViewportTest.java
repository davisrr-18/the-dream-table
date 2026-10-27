package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportTest {
	@Test
	void scrollMovesContentButKeepsPanelAndViewportFixed() {
		ScreenGeometry geometry = ScreenGeometry.calculate(640, 360, 24, false);
		Viewport viewport = new Viewport();
		viewport.layout(geometry, 7, 700);

		int panelY = viewport.originY();
		int contentY = viewport.cardY(0);
		assertTrue(viewport.scrollBy(-1));

		assertEquals(panelY, viewport.originY());
		assertEquals(geometry.viewport().y(), viewport.contentTop());
		assertTrue(viewport.cardY(0) < contentY);
	}

	@Test
	void revealingAControlScrollsTheSmallestNecessaryDistance() {
		ScreenGeometry geometry = ScreenGeometry.calculate(320, 240, 0, false);
		Viewport viewport = new Viewport();
		viewport.layout(geometry, 2, 600);
		UiRect target = new UiRect(viewport.cardX(), viewport.cardY(8), 168, 32);

		assertTrue(viewport.reveal(target));

		assertEquals(geometry.viewport().bottom(), target.bottom() - viewport.scroll());
		assertFalse(viewport.reveal(new UiRect(viewport.cardX(), viewport.cardY(8), 168, 32)));
	}

	@Test
	void slotsMustBeFullyInsideTheViewportToDrawOrReceiveInput() {
		ScreenGeometry geometry = ScreenGeometry.calculate(320, 240, 0, true);
		Viewport viewport = new Viewport();
		viewport.layout(geometry, 1, 500);

		assertTrue(viewport.fullyVisible(new UiRect(geometry.slotRegion().x() + 10, geometry.viewport().y() + 10, 16, 16)));
		assertFalse(viewport.fullyVisible(new UiRect(geometry.slotRegion().x() + 10, geometry.viewport().y() - 1, 16, 16)));
		assertFalse(viewport.fullyVisible(new UiRect(geometry.slotRegion().right() - 15, geometry.viewport().y() + 10, 16, 16)));
	}

	@Test
	void wideGridUsesTwoColumnsWhileCompactKeepsOne() {
		Viewport compact = new Viewport();
		compact.layout(ScreenGeometry.calculate(416, 240, 0, false), 0, 300);
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		assertEquals(compact.gridCardX(0), compact.gridCardX(1));
		assertTrue(compact.gridCardY(1) > compact.gridCardY(0));
		assertTrue(wide.gridCardX(1) > wide.gridCardX(0));
		assertEquals(wide.gridCardY(0), wide.gridCardY(1));
		assertEquals(wide.gridCardX(0), wide.gridCardX(2));
		assertTrue(wide.gridCardY(2) > wide.gridCardY(0));
	}
}
