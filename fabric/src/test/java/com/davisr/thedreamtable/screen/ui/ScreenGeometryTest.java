package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.LayoutMode;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenGeometryTest {
	@Test
	void switchesFromCompactToWideAt420Pixels() {
		assertEquals(LayoutMode.COMPACT, LayoutMode.forWidth(320));
		assertEquals(LayoutMode.COMPACT, LayoutMode.forWidth(416));
		assertEquals(LayoutMode.WIDE, LayoutMode.forWidth(420));
		assertEquals(LayoutMode.WIDE, LayoutMode.forWidth(640));
		assertEquals(LayoutMode.WIDE, LayoutMode.forWidth(854));
	}

	@Test
	void centersTheChosenPanelAndCapsItsHeight() {
		assertEquals(new UiRect(62, 4, 196, 232), ScreenGeometry.calculate(320, 240, 0, false).panel());
		assertEquals(new UiRect(18, 4, 384, 232), ScreenGeometry.calculate(420, 240, 0, false).panel());
		assertEquals(new UiRect(128, 52, 384, 256), ScreenGeometry.calculate(640, 360, 0, false).panel());
		assertEquals(new UiRect(235, 112, 384, 256), ScreenGeometry.calculate(854, 480, 0, false).panel());
	}

	@Test
	void keepsAFixedPanelSizeOnScreensWhereItFits() {
		for (int[] screen : new int[][] {{420, 264}, {480, 270}, {640, 360}, {854, 480}, {1920, 1080}}) {
			UiRect panel = ScreenGeometry.calculate(screen[0], screen[1], 0, false).panel();
			assertEquals(LayoutMode.WIDE.panelWidth(), panel.width());
			assertEquals(ScreenGeometry.PANEL_HEIGHT, panel.height());
		}
		assertEquals(ScreenGeometry.PANEL_HEIGHT - 6, ScreenGeometry.calculate(455, 258, 0, false).panel().height());
	}

	@Test
	void keepsFixedChromeOutsideTheScrollingViewport() {
		ScreenGeometry geometry = ScreenGeometry.calculate(640, 360, 24, false);

		assertEquals(geometry.panel().x(), geometry.header().x());
		assertEquals(geometry.header().bottom(), geometry.tabs().y());
		assertEquals(geometry.tabs().bottom(), geometry.toolbar().y());
		assertEquals(24, geometry.toolbar().height());
		assertEquals(geometry.toolbar().bottom(), geometry.viewport().y());
		assertEquals(geometry.panel().bottom(), geometry.viewport().bottom());
		assertEquals(geometry.viewport().x() + ScreenGeometry.CONTENT_INSET, geometry.contentOrigin().x());
		assertEquals(geometry.viewport().y(), geometry.contentOrigin().y());
	}

	@Test
	void providesOneOrTwo168PixelContentColumns() {
		ScreenGeometry compact = ScreenGeometry.calculate(416, 240, 0, false);
		ScreenGeometry wide = ScreenGeometry.calculate(420, 240, 0, false);

		assertEquals(1, compact.columns().size());
		assertEquals(168, compact.columns().getFirst().width());
		assertEquals(2, wide.columns().size());
		assertEquals(168, wide.columns().getFirst().width());
		assertEquals(168, wide.columns().getLast().width());
	}

	@Test
	void reservesA196PixelSlotRegionOnSlotPages() {
		ScreenGeometry compact = ScreenGeometry.calculate(320, 240, 0, true);
		ScreenGeometry wide = ScreenGeometry.calculate(640, 360, 0, true);

		assertEquals(196, compact.slotRegion().width());
		assertEquals(compact.panel().x(), compact.slotRegion().x());
		assertEquals(wide.panel().width(), wide.slotRegion().width());
		assertEquals(wide.panel().x(), wide.slotRegion().x());
		assertEquals(compact.slotRegion().x(), compact.slotOrigin().x());
		assertEquals(wide.slotRegion().x(), wide.slotOrigin().x());
	}

	@Test
	void rectangleBordersAreRightAndBottomExclusive() {
		UiRect rectangle = new UiRect(10, 20, 30, 40);

		assertTrue(rectangle.contains(10, 20));
		assertTrue(rectangle.contains(39.999, 59.999));
		assertFalse(rectangle.contains(40, 20));
		assertFalse(rectangle.contains(10, 60));
		assertTrue(rectangle.contains(new UiRect(10, 20, 30, 40)));
		assertFalse(rectangle.contains(new UiRect(10, 20, 31, 40)));
	}

	@Test
	void capsBadgeCountsWithoutChangingSmallerCounts() {
		assertEquals("0", BadgeText.of(0));
		assertEquals("1", BadgeText.of(1));
		assertEquals("99", BadgeText.of(99));
		assertEquals("99+", BadgeText.of(100));
		assertEquals("99+", BadgeText.of(Integer.MAX_VALUE));
	}
}
