package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.farm.FarmTemplate;

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

	public static int gridRows() {
		return (FarmTemplate.MAX_SLOTS + COLUMNS - 1) / COLUMNS;
	}

	public static int backY() {
		return HEADER + 8 + gridRows() * GRID + 6;
	}

	public static int inventoryY() {
		return backY() + BUTTON + 14;
	}

	public static int requirementsHeight() {
		return inventoryY() + 3 * 18 + 4 + 18 + 12;
	}
}
