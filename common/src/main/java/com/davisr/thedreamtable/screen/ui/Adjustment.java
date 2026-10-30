package com.davisr.thedreamtable.screen.ui;

/** How the keyboard changes a focused slider: Left and Right step it, Home and End jump to its ends. */
public enum Adjustment {
	DECREASE,
	INCREASE,
	MINIMUM,
	MAXIMUM;

	/** The value after this adjustment, kept within {@code min} to {@code max}. */
	public int apply(int value, int step, int min, int max) {
		int next = switch (this) {
			case DECREASE -> value - step;
			case INCREASE -> value + step;
			case MINIMUM -> min;
			case MAXIMUM -> max;
		};
		return Math.max(min, Math.min(max, next));
	}
}
