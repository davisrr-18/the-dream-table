package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveCardPagesLayoutTest {
	private static final List<Class<?>> CARD_PAGES = List.of(CategoryPage.class, MaterialsPage.class, SwapPage.class, AlertsPage.class);

	@Test
	void cardCollectionsAndBackControlsUseTheSameResponsiveGrid() throws ReflectiveOperationException {
		Viewport compact = new Viewport();
		compact.layout(ScreenGeometry.calculate(416, 240, 0, false), 0, 300);
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		for (Class<?> page : CARD_PAGES) {
			assertEquals(compact.gridCardX(1), invoke(page, "cardX", compact, 1));
			assertEquals(compact.gridCardY(1), invoke(page, "cardY", compact, 1));
			assertEquals(wide.gridCardX(1), invoke(page, "cardX", wide, 1));
			assertEquals(wide.gridCardY(1), invoke(page, "cardY", wide, 1));
			assertTrue(invoke(page, "backY", wide, 2) > invoke(page, "cardY", wide, 1) + CARD_HEIGHT);
			assertTrue(invoke(page, "contentHeight", wide, 2) >= invoke(page, "backY", wide, 2) - wide.contentTop() + BUTTON_HEIGHT,
					page.getSimpleName());
		}
	}

	@Test
	void alertsBackControlFollowsTheLastCardInItsGridRow() throws ReflectiveOperationException {
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		assertEquals(wide.gridCardY(1) + CARD_HEIGHT + 10, invoke(AlertsPage.class, "backY", wide, 2));
	}

	private static int invoke(Class<?> page, String methodName, Viewport view, int count) throws ReflectiveOperationException {
		Method method = page.getDeclaredMethod(methodName, Viewport.class, int.class);
		method.setAccessible(true);
		return (int) method.invoke(null, view, count);
	}
}
