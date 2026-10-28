package com.davisr.thedreamtable.screen.ui.layout;

public enum LayoutMode {
	COMPACT(196),
	WIDE(384);

	public static final int WIDE_BREAKPOINT = 420;

	private final int panelWidth;

	LayoutMode(int panelWidth) {
		this.panelWidth = panelWidth;
	}

	public int panelWidth() {
		return panelWidth;
	}

	public static LayoutMode forWidth(int screenWidth) {
		return screenWidth < WIDE_BREAKPOINT ? COMPACT : WIDE;
	}
}
