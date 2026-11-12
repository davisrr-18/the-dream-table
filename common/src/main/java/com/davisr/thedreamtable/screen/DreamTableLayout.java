package com.davisr.thedreamtable.screen;

public final class DreamTableLayout {
	public static final int PANEL_WIDTH = 196;
	public static final int HEADER = 46;
	public static final int BUTTON = 28;
	public static final int GRID = 28;
	public static final int COLUMNS = 9;

	private DreamTableLayout() {
	}

	public static int materialX(int index) {
		return 17 + (index % COLUMNS) * 18;
	}

	public static int materialY(int index) {
		return HEADER + 8 + (index / COLUMNS) * GRID;
	}

	public static int inventoryX() {
		return (PANEL_WIDTH - 162) / 2;
	}

	public static int rowsFor(int materials) {
		return Math.max(1, (Math.max(materials, 1) + COLUMNS - 1) / COLUMNS);
	}

	public static int backY(int materials) {
		return HEADER + 8 + rowsFor(materials) * GRID + 6;
	}

	public static int inventoryY(int materials) {
		return backY(materials) + BUTTON + 14;
	}

	public static int inventoryY() {
		return inventoryY(1);
	}

	/** Below the player inventory and the water row (inventoryY + 78), where the delivery buttons sit. */
	public static int deliverAllY(int materials) {
		return inventoryY(materials) + 92;
	}

	public static int deliverNearbyY(int materials) {
		return deliverAllY(materials) + BUTTON + 6;
	}

	public static int requirementsHeight(int materials) {
		return deliverNearbyY(materials) + BUTTON + 8;
	}
}
