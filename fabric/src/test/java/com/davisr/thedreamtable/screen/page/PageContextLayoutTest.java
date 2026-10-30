package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.screen.ui.ControlSpec;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageContextLayoutTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void registeredFlowControlsUseTheirVisibleBoundsForHitFocusAndActivationAtEveryResolution() throws ReflectiveOperationException {
		for (int[] resolution : java.util.List.of(
				new int[] {320, 240}, new int[] {384, 240}, new int[] {416, 240},
				new int[] {420, 240}, new int[] {640, 360}, new int[] {854, 480})) {
			Viewport view = new Viewport();
			view.layout(ScreenGeometry.calculate(resolution[0], resolution[1], 0, false), 0, 300);
			CapturePage page = new CapturePage();
			PageContext context = new PageContext(null, view, null, null);
			context.beginControls(7, page);
			int x = view.flowX();
			int y = view.cardY(0);
			register(context, "flow", x, y);

			ControlSpec control = context.controls().getFirst();
			assertEquals(x, control.bounds().x());
			assertEquals(y, control.bounds().y());
			assertTrue(context.buttonHit(x + 1, y + 1, x, y));
			context.action(control.id()).run();
			assertEquals(x + 84, page.x);
			assertEquals(y + BUTTON_HEIGHT / 2, page.y);
			// Buttons placed in the flow column span the whole collection; cards keep their own width.
			assertTrue(context.buttonHit(view.collectionX() + 1, y + 1, x, y));
			// A card keeps its own width unless the page gives it the flow width.
			if (view.collectionWidth() > 168) {
				assertFalse(context.cardHit(view.collectionX() + 169, y + 1, x, y));
				assertTrue(context.cardHit(view.collectionX() + 169, y + 1, x, y, view.flowWidth()));
			}
		}
	}

	private static void register(PageContext context, String key, int x, int y) throws ReflectiveOperationException {
		Method register = PageContext.class.getDeclaredMethod("register", String.class, Text.class, Text.class,
				int.class, int.class, int.class, boolean.class, boolean.class);
		register.setAccessible(true);
		register.invoke(context, key, Text.literal("Flow"), Text.empty(), x, y, BUTTON_HEIGHT, false, true);
	}

	private static final class CapturePage implements TablePage {
		private double x;
		private double y;

		@Override
		public void draw(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY) {
		}

		@Override
		public boolean click(double mouseX, double mouseY) {
			x = mouseX;
			y = mouseY;
			return true;
		}

		@Override
		public int contentHeight() {
			return 0;
		}

		@Override
		public Text subtitle() {
			return Text.empty();
		}
	}
}
