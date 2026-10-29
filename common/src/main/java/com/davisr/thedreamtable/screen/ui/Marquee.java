package com.davisr.thedreamtable.screen.ui;

/** The back-and-forth scroll vanilla buttons use for text wider than its box, as a pure function of time. */
public final class Marquee {
	private Marquee() {
	}

	/** Pixels to move the text left of the box's left edge at {@code millis}; 0 when the text fits. */
	public static int offset(int textWidth, int boxWidth, long millis) {
		int overflow = textWidth - boxWidth;
		if (overflow <= 0) {
			return 0;
		}
		double seconds = millis / 1000.0;
		double period = Math.max(overflow * 0.5, 3.0);
		double eased = Math.sin(Math.PI / 2.0 * Math.cos(Math.PI * 2.0 * seconds / period)) / 2.0 + 0.5;
		return (int) (eased * overflow);
	}
}
