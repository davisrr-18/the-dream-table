package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedFarmsPageLayoutTest {
	@Test
	void putsTheFirstTwoFarmCardsOnTheSameRowInWideMode() throws ReflectiveOperationException {
		Viewport view = new Viewport();
		view.layout(ScreenGeometry.calculate(768, 480, 26, false), 0, 500);
		LoadedFarmsPage page = new LoadedFarmsPage(new PageContext(null, view, null, null), null);
		Method cardY = LoadedFarmsPage.class.getDeclaredMethod("cardY", int.class);
		cardY.setAccessible(true);

		assertEquals(cardY.invoke(page, 0), cardY.invoke(page, 1));
	}

	@Test
	void listsFarmsEvenWhenTheServerLimitIsZero() {
		assertTrue(LoadedFarmsPage.lists(true, 3));
		assertTrue(!LoadedFarmsPage.lists(true, 0));
		assertTrue(!LoadedFarmsPage.lists(false, 3));
	}

	@Test
	void putsTheFourFilterChipsInTwoRowsOnlyWhenTheViewportIsWide() throws ReflectiveOperationException {
		Viewport compact = new Viewport();
		compact.layout(ScreenGeometry.calculate(416, 240, 26, false), 0, 500);
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 26, false), 0, 500);

		Method chipY = LoadedFarmsPage.class.getDeclaredMethod("chipY", int.class);
		chipY.setAccessible(true);
		LoadedFarmsPage compactPage = new LoadedFarmsPage(new PageContext(null, compact, null, null), null);
		LoadedFarmsPage widePage = new LoadedFarmsPage(new PageContext(null, wide, null, null), null);

		assertTrue((int) chipY.invoke(compactPage, 1) > (int) chipY.invoke(compactPage, 0));
		assertEquals(chipY.invoke(widePage, 0), chipY.invoke(widePage, 1));
		assertTrue((int) chipY.invoke(widePage, 2) > (int) chipY.invoke(widePage, 1));
		assertEquals(compact.gridCardX(0), LoadedFarmsPage.filterX(compact, 0));
		assertEquals(compact.gridCardX(0), LoadedFarmsPage.filterX(compact, 1));
		assertEquals(wide.gridCardX(0), LoadedFarmsPage.filterX(wide, 0));
		assertEquals(wide.gridCardX(1), LoadedFarmsPage.filterX(wide, 1));
		assertEquals(5, LoadedFarmsPage.filterRows(compact, 5));
		assertEquals(3, LoadedFarmsPage.filterRows(wide, 5));
		assertTrue(LoadedFarmsPage.listHeight(wide, 4) < LoadedFarmsPage.listHeight(compact, 4));
	}
}
