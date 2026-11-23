package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feedback f6d6cc (Forge 1.20.1, v1.5.0): "the bamboo material cannot be added, with 50 in the inventory".
 * A slot only takes clicks while all 16x16 pixels are inside the scroll view (Viewport.fullyVisible). On the Requirements
 * page the bamboo card and the player inventory must be reachable together (a whole inventory row) at some scroll position, at every screen size. Result 2026-11-23: it holds, so the geometry is not the cause of the report.
 */
class BambooRequirementsReachTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static boolean slotVisible(Viewport view, ScreenGeometry geometry, int slotX, int slotY) {
		return view.fullyVisible(new UiRect(geometry.slotOrigin().x() + slotX, view.slotOriginY() + slotY, 16, 16));
	}

	@Test
	void bambooCardAndInventoryCanBeVisibleTogether() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.BAMBOO.id());
		assertNotNull(template);
		int materials = template.materials().size();
		int bamboo = -1;
		for (int index = 0; index < materials; index++) {
			if (template.materials().get(index).item() == Items.BAMBOO) {
				bamboo = index;
			}
		}
		assertTrue(bamboo >= 0, "the bamboo farm asks for bamboo");
		StringBuilder failures = new StringBuilder();
		int[] widths = {320, 416, 420, 480, 640, 854};
		int[] heights = {180, 240, 270, 360, 480};
		for (int width : widths) {
			for (int height : heights) {
				ScreenGeometry geometry = ScreenGeometry.calculate(width, height, 0, true);
				int columns = geometry.mode().panelWidth() > 0 && width >= 420 ? 2 : 1;
				Viewport view = new Viewport();
				view.layout(geometry, DreamTableScreenHandler.PAGE_REQUIREMENTS, RequirementsLayout.contentHeight(materials, columns));
				boolean reachable = false;
				int bestInventory = 0;
				for (int notch = 0; notch <= 200 && !reachable; notch++) {
					int inventoryShown = 0;
					for (int slot = 0; slot < 36; slot++) {
						if (slotVisible(view, geometry, RequirementsLayout.playerSlotX(slot, columns), RequirementsLayout.playerSlotY(slot, materials, columns))) {
							inventoryShown++;
						}
					}
					boolean bambooShown = slotVisible(view, geometry, RequirementsLayout.slotX(bamboo, columns), RequirementsLayout.slotY(bamboo, columns));
					bestInventory = Math.max(bestInventory, bambooShown ? inventoryShown : 0);
					reachable = bambooShown && inventoryShown >= 9;
					if (!view.scrollBy(-1)) {
						break;
					}
				}
				if (!reachable) {
					failures.append(String.format("%dx%d (%d column%s, bamboo is card %d of %d): never both visible%n", width, height, columns, columns == 1 ? "" : "s", bamboo, materials));
				}
			}
		}
		assertTrue(failures.length() == 0, "bamboo slot and inventory never visible together:\n" + failures);
	}
}
