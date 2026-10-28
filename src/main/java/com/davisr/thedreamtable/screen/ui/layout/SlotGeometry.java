package com.davisr.thedreamtable.screen.ui.layout;

public final class SlotGeometry {
	private SlotGeometry() {
	}

	public static boolean interactive(boolean pageAllowsSlots, UiRect viewport, UiRect slotBounds) {
		return pageAllowsSlots && viewport.contains(slotBounds);
	}
}
