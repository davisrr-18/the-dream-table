package com.davisr.thedreamtable.screen.ui;

public final class BadgeText {
	private BadgeText() {
	}

	public static String of(int count) {
		if (count > 99) {
			return "99+";
		}
		return Integer.toString(Math.max(0, count));
	}
}
