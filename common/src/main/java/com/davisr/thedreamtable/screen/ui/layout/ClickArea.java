package com.davisr.thedreamtable.screen.ui.layout;

/** Decides whether a click reaches a custom widget: only while it is usable and the pointer is inside its box. */
public final class ClickArea {
	private ClickArea() {
	}

	public static boolean hits(boolean usable, UiRect box, double mouseX, double mouseY) {
		return usable && box.contains(mouseX, mouseY);
	}
}
