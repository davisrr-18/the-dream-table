package com.davisr.thedreamtable.screen;

import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
				int inventoryEnd = RequirementsLayout.inventoryY(materials, columns) + RequirementsLayout.inventoryHeight(columns);
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

	@Test
	void wideInventoryUsesTheFlowWidthWithTheHotbarEndingTheSecondRow() {
		int left = RequirementsLayout.columnX(0);
		int right = left + RequirementsLayout.flowWidth(2);
		assertTrue(RequirementsLayout.inventoryX(2) >= left);
		assertTrue(RequirementsLayout.inventoryX(2) + RequirementsLayout.inventoryWidth(2) <= right);
		assertEquals(RequirementsLayout.inventoryY(5, 2), RequirementsLayout.playerSlotY(0, 5, 2));
		assertEquals(RequirementsLayout.inventoryY(5, 2) + 18, RequirementsLayout.playerSlotY(27, 5, 2));
		assertTrue(RequirementsLayout.playerSlotX(0, 2) - 2 >= left);
		assertTrue(RequirementsLayout.playerSlotX(17, 2) + 18 <= right);
		for (int column = 1; column < 18; column++) {
			assertEquals(RequirementsLayout.playerSlotX(1, 2) - RequirementsLayout.playerSlotX(0, 2),
					RequirementsLayout.playerSlotX(column, 2) - RequirementsLayout.playerSlotX(column - 1, 2), "equal pitch keeps every border the same thickness");
		}
		assertTrue(RequirementsLayout.playerSlotX(1, 2) - RequirementsLayout.playerSlotX(0, 2) >= 18, "slots never overlap");
		assertEquals(RequirementsLayout.playerSlotX(9, 2), RequirementsLayout.playerSlotX(27, 2));
	}

	@Test
	void completeCardsMoveAfterTheMissingOnesKeepingTheirOrder() {
		assertArrayEquals(new int[] {0, 2, 1, 3}, RequirementsLayout.order(new boolean[] {false, true, false, true}));
		assertArrayEquals(new int[] {0, 1, 2}, RequirementsLayout.order(new boolean[] {false, false, false}));
		assertArrayEquals(new int[] {}, RequirementsLayout.order(new boolean[] {}));
	}
}
