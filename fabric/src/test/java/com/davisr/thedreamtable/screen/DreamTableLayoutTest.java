package com.davisr.thedreamtable.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Requirements page stacks grid, Back, inventory, water line and two buttons; for every material count they must not overlap. */
class DreamTableLayoutTest {
	@Test
	void theStackIsInOrderForEveryMaterialCount() {
		for (int materials = 0; materials <= 54; materials++) {
			int lastGridRow = DreamTableLayout.materialY(Math.max(materials, 1) - 1) + 18;
			assertTrue(DreamTableLayout.backY(materials) >= lastGridRow, "Back under the grid for " + materials);
			assertTrue(DreamTableLayout.inventoryY(materials) - 12 >= DreamTableLayout.backY(materials) + DreamTableLayout.BUTTON, "inventory label under Back for " + materials);
			int hotbarEnd = DreamTableLayout.inventoryY(materials) + 3 * 18 + 4 + 18;
			assertTrue(DreamTableLayout.inventoryY(materials) + 78 >= hotbarEnd, "water line under the hotbar for " + materials);
			assertTrue(DreamTableLayout.deliverAllY(materials) >= DreamTableLayout.inventoryY(materials) + 78 + 10, "Deliver All under the water line for " + materials);
			assertTrue(DreamTableLayout.deliverNearbyY(materials) >= DreamTableLayout.deliverAllY(materials) + DreamTableLayout.BUTTON, "buttons do not overlap for " + materials);
			assertTrue(DreamTableLayout.requirementsHeight(materials) >= DreamTableLayout.deliverNearbyY(materials) + DreamTableLayout.BUTTON, "page holds both buttons for " + materials);
		}
	}
}
