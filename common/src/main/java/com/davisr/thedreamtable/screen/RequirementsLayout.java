package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * Where everything on the Requirements page goes, from the number of material cards per row (one on compact screens,
 * two on wide ones) and the number of materials. All coordinates are relative to the slot origin (the panel's left edge
 * and the viewport top minus {@link DreamTableLayout#HEADER}), so the handler's slots and the page agree on them.
 */
public final class RequirementsLayout {
	public static final int INVENTORY_WIDTH = 9 * 18;
	/** The inventory is three rows, a 4 px gap, then the hotbar. */
	public static final int INVENTORY_HEIGHT = 3 * 18 + 4 + 18;
	private static final int CARD_STEP = CARD_HEIGHT + CARD_GAP;

	private RequirementsLayout() {
	}

	public static int columnX(int column) {
		return ScreenGeometry.CONTENT_INSET + column * (ScreenGeometry.COLUMN_WIDTH + ScreenGeometry.COLUMN_GAP);
	}

	public static int flowWidth(int columns) {
		return columns * ScreenGeometry.COLUMN_WIDTH + (columns - 1) * ScreenGeometry.COLUMN_GAP;
	}

	public static int rows(int materials, int columns) {
		return (materials + columns - 1) / columns;
	}

	public static int cardX(int index, int columns) {
		return columnX(index % columns);
	}

	public static int cardY(int index, int columns) {
		return DreamTableLayout.HEADER + PAD + index / columns * CARD_STEP;
	}

	/** The slot sits on the card's icon box (the card paints its icon at x + 9, y + 8). */
	public static int slotX(int index, int columns) {
		return cardX(index, columns) + 9;
	}

	public static int slotY(int index, int columns) {
		return cardY(index, columns) + 8;
	}

	public static int cardsEnd(int materials, int columns) {
		return DreamTableLayout.HEADER + PAD + rows(materials, columns) * CARD_STEP - (materials == 0 ? 0 : CARD_GAP);
	}

	/** The water line is one text row under the cards; the space is kept for farms that need no water. */
	public static int waterY(int materials, int columns) {
		return cardsEnd(materials, columns) + 6;
	}

	public static int inventoryLabelY(int materials, int columns) {
		return cardsEnd(materials, columns) + 20;
	}

	public static int inventoryX(int columns) {
		return ScreenGeometry.CONTENT_INSET + (flowWidth(columns) - INVENTORY_WIDTH) / 2;
	}

	public static int inventoryY(int materials, int columns) {
		return cardsEnd(materials, columns) + 32;
	}

	public static int deliverAllY(int materials, int columns) {
		return inventoryY(materials, columns) + INVENTORY_HEIGHT + 12;
	}

	public static int deliverNearbyY(int materials, int columns) {
		return deliverAllY(materials, columns) + BUTTON_HEIGHT + BUTTON_GAP;
	}

	public static int backY(int materials, int columns) {
		return deliverNearbyY(materials, columns) + BUTTON_HEIGHT + BUTTON_GAP;
	}

	/** Height of the scrolling content, from the top of the viewport. */
	public static int contentHeight(int materials, int columns) {
		return backY(materials, columns) + BUTTON_HEIGHT + 8 - DreamTableLayout.HEADER;
	}
}
