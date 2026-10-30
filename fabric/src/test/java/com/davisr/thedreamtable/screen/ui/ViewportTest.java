package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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

	@Test
	void exposesCenteredFlowAndFullCollectionToolbarAnchors() {
		Viewport compact = new Viewport();
		compact.layout(ScreenGeometry.calculate(416, 240, 0, false), 0, 300);
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		assertEquals(compact.cardX(), compact.flowX());
		assertEquals(168, compact.collectionWidth());
		assertEquals(wide.collectionX(), wide.flowX());
		assertEquals(wide.collectionWidth(), wide.flowWidth());
		assertEquals(wide.gridCardX(1) + 168 - wide.gridCardX(0), wide.collectionWidth());
	}

	@Test
	void keepsFlowAndCollectionAnchorsInsideEverySupportedResolution() {
		for (int[] resolution : java.util.List.of(
				new int[] {320, 240}, new int[] {384, 240}, new int[] {416, 240},
				new int[] {420, 240}, new int[] {640, 360}, new int[] {854, 480})) {
			Viewport viewport = new Viewport();
			viewport.layout(ScreenGeometry.calculate(resolution[0], resolution[1], 0, false), 0, 300);

			assertTrue(viewport.flowX() >= viewport.collectionX());
			assertTrue(viewport.flowX() + viewport.flowWidth() <= viewport.collectionX() + viewport.collectionWidth());
			assertTrue(viewport.collectionX() + viewport.collectionWidth() <= viewport.contentRight());
		}
	}

	@Test
	void alignsCollectionAndSingleFlowTextOnTheSharedVisualCenterInWideMode() {
		Viewport view = new Viewport();
		view.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		assertEquals(view.collectionX() + view.collectionWidth() / 2, view.collectionCenterX());
		assertEquals(view.flowX() + view.flowWidth() / 2, view.flowCenterX());
		assertEquals(view.collectionCenterX(), view.flowCenterX());
	}

	@Test
	void scrollbarTrackStartsUnderTheTabsWithOrWithoutAToolbar() throws ReflectiveOperationException {
		Method trackY = Viewport.class.getDeclaredMethod("trackY");
		trackY.setAccessible(true);
		Method trackHeight = Viewport.class.getDeclaredMethod("trackHeight");
		trackHeight.setAccessible(true);
		for (int[] screen : new int[][] {{320, 240}, {420, 240}, {640, 360}, {854, 480}}) {
			int plainTop = 0;
			for (int toolbar : new int[] {0, 26, 52}) {
				ScreenGeometry geometry = ScreenGeometry.calculate(screen[0], screen[1], toolbar, false);
				Viewport viewport = new Viewport();
				viewport.layout(geometry, 0, 900);

				int top = (int) trackY.invoke(viewport);
				if (toolbar == 0) {
					plainTop = top;
				}
				assertEquals(plainTop, top);
				assertEquals(geometry.tabs().bottom() + Theme.PAD, top);
				assertEquals(geometry.viewport().bottom() - 10, top + (int) trackHeight.invoke(viewport));
			}
		}
	}

	@Test
	void scrollbarStaysOutsideTheLastContentColumn() throws ReflectiveOperationException {
		Method trackX = Viewport.class.getDeclaredMethod("trackX");
		trackX.setAccessible(true);
		for (ScreenGeometry geometry : java.util.List.of(
				ScreenGeometry.calculate(384, 240, 26, false),
				ScreenGeometry.calculate(640, 360, 26, false))) {
			Viewport viewport = new Viewport();
			viewport.layout(geometry, 0, 500);

			assertTrue((int) trackX.invoke(viewport) > geometry.columns().getLast().right());
		}
	}

	@Test
	void keepsPanelColumnsAndScrollbarInsideEverySupportedResolution() throws ReflectiveOperationException {
		Method trackX = Viewport.class.getDeclaredMethod("trackX");
		trackX.setAccessible(true);
		for (int[] resolution : java.util.List.of(
				new int[] {320, 240}, new int[] {384, 240}, new int[] {416, 240},
				new int[] {420, 240}, new int[] {640, 360}, new int[] {854, 480})) {
			ScreenGeometry geometry = ScreenGeometry.calculate(resolution[0], resolution[1], 0, false);
			Viewport viewport = new Viewport();
			viewport.layout(geometry, 0, 500);

			assertTrue(geometry.panel().x() >= 0);
			assertTrue(geometry.panel().right() <= resolution[0]);
			assertTrue(geometry.panel().y() >= 0);
			assertTrue(geometry.panel().bottom() <= resolution[1]);
			assertTrue(geometry.columns().stream().allMatch(column ->
					column.x() >= viewport.contentLeft() && column.right() <= viewport.contentRight()));
			int scrollbarX = (int) trackX.invoke(viewport);
			assertTrue(scrollbarX > geometry.columns().getLast().right());
			assertTrue(scrollbarX + 8 <= geometry.viewport().right());
		}
	}

	@Test
	void acceptsGridCardClicksOnlyInsideTheVisibleViewportAtEverySupportedResolution() {
		for (int[] resolution : java.util.List.of(
				new int[] {320, 240}, new int[] {384, 240}, new int[] {416, 240},
				new int[] {420, 240}, new int[] {640, 360}, new int[] {854, 480})) {
			Viewport viewport = new Viewport();
			viewport.layout(ScreenGeometry.calculate(resolution[0], resolution[1], 0, false), 0, 500);
			for (int index = 0; index < 2; index++) {
				int x = viewport.gridCardX(index);
				int y = viewport.gridCardY(index);
				assertTrue(viewport.hit(x + 1, y + 1, x, y, 168, 32));
			}
			assertFalse(viewport.hit(viewport.contentRight(), viewport.cardY(0), viewport.cardX(), viewport.cardY(0), 168, 32));
		}
	}

	@Test
	void computesContentHeightAfterInstallingTheNewResizeGeometry() throws ReflectiveOperationException {
		Viewport viewport = new Viewport();
		ScreenGeometry wide = ScreenGeometry.calculate(420, 240, 0, false);
		ScreenGeometry compact = ScreenGeometry.calculate(416, 240, 0, false);
		viewport.layout(wide, 0, 0);
		viewport.layout(compact, 0, () -> viewport.gridContentHeight(10));

		Method maxScroll = Viewport.class.getDeclaredMethod("maxScroll");
		maxScroll.setAccessible(true);
		assertEquals(217, maxScroll.invoke(viewport));
	}

	@Test
	void aViewportWithoutAContentAreaReportsItSoNothingIsDrawn() {
		Viewport empty = new Viewport();
		// A toolbar taller than the whole panel leaves the content area with no height.
		empty.layout(320, 240, 0, 1000, 100);
		assertFalse(empty.hasContentArea());

		Viewport normal = new Viewport();
		normal.layout(ScreenGeometry.calculate(320, 240, 0, false), 0, 100);
		assertTrue(normal.hasContentArea());
	}
}
