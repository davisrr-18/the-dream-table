package com.davisr.thedreamtable.screen;

import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Requirements page stacks cards, water line, inventory and three buttons; for every material count and column count they must not overlap. */
class DreamTableLayoutTest {
	@Test
	void theStackIsInOrderForEveryMaterialAndColumnCount() {
		for (int columns = 1; columns <= 2; columns++) {
			for (int materials = 0; materials <= 54; materials++) {
				String at = " for " + materials + " materials in " + columns + " columns";
				int lastCard = materials == 0 ? 0 : RequirementsLayout.cardY(materials - 1, columns) + 32;
				assertTrue(RequirementsLayout.cardsEnd(materials, columns) >= lastCard, "cards end after the last card" + at);
				assertTrue(RequirementsLayout.waterY(materials, columns) >= RequirementsLayout.cardsEnd(materials, columns), "water line under the cards" + at);
				assertTrue(RequirementsLayout.inventoryLabelY(materials, columns) >= RequirementsLayout.waterY(materials, columns) + 9, "label under the water line" + at);
				assertTrue(RequirementsLayout.inventoryY(materials, columns) >= RequirementsLayout.inventoryLabelY(materials, columns) + 9, "inventory under its label" + at);
				int inventoryEnd = RequirementsLayout.inventoryY(materials, columns) + RequirementsLayout.INVENTORY_HEIGHT;
				assertTrue(RequirementsLayout.deliverAllY(materials, columns) >= inventoryEnd, "Deliver All under the inventory" + at);
				assertTrue(RequirementsLayout.deliverNearbyY(materials, columns) >= RequirementsLayout.deliverAllY(materials, columns) + BUTTON_HEIGHT, "buttons do not overlap" + at);
				assertTrue(RequirementsLayout.backY(materials, columns) >= RequirementsLayout.deliverNearbyY(materials, columns) + BUTTON_HEIGHT, "Back under the buttons" + at);
			}
		}
	}

	@Test
	void everySlotSitsOnItsCardAndTheInventoryIsCentered() {
		assertEquals(RequirementsLayout.cardX(3, 2) + 9, RequirementsLayout.slotX(3, 2));
		assertEquals(RequirementsLayout.cardY(3, 2) + 8, RequirementsLayout.slotY(3, 2));
		// Two cards per row: even indexes left, odd indexes right, same row.
		assertTrue(RequirementsLayout.cardX(1, 2) > RequirementsLayout.cardX(0, 2));
		assertEquals(RequirementsLayout.cardY(0, 2), RequirementsLayout.cardY(1, 2));
		assertEquals(17, RequirementsLayout.inventoryX(1));
	}
}
