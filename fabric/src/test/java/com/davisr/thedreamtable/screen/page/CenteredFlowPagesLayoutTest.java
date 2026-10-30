package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CenteredFlowPagesLayoutTest {
	private static final List<Class<?>> FLOW_PAGES = List.of(WorkPage.class, SpeedPage.class, FarmPage.class, InvitesPage.class, FarmInfoPage.class);

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void singleColumnPagesUseTheViewportFlowAnchorInWideMode() throws ReflectiveOperationException {
		Viewport view = new Viewport();
		view.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 300);

		for (Class<?> page : FLOW_PAGES) {
			assertEquals(view.flowX(), invokeFlowX(page, view), page.getSimpleName());
		}
	}

	private static int invokeFlowX(Class<?> page, Viewport view) throws ReflectiveOperationException {
		Constructor<?> constructor = switch (page.getSimpleName()) {
			case "FarmPage" -> page.getDeclaredConstructor(PageContext.class, com.davisr.thedreamtable.screen.ui.SearchBox.class);
			default -> page.getDeclaredConstructor(PageContext.class);
		};
		constructor.setAccessible(true);
		Object instance = page.getSimpleName().equals("FarmPage")
				? constructor.newInstance(new PageContext(null, view, null, null), null)
				: constructor.newInstance(new PageContext(null, view, null, null));
		Method method = page.getDeclaredMethod("flowX");
		method.setAccessible(true);
		return (int) method.invoke(instance);
	}
}
