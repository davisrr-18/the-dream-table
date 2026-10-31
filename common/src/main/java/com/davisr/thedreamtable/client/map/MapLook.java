package com.davisr.thedreamtable.client.map;

/**
 * The mouse look of map mode, scaled as the game scales it: the sensitivity option becomes a factor on the raw pixels the mouse
 * moved, and each unit turns the view by 0.15 degrees. Pure, so it is tested without a client.
 */
public final class MapLook {
	/** Degrees turned per unit of scaled mouse movement, as {@code Entity.changeLookDirection} does. */
	private static final double DEGREES_PER_UNIT = 0.15;
	/** A movement bigger than this many raw pixels between two events is a jump of the pointer, not a turn of the mouse. */
	private static final double MAX_PIXELS = 200.0;

	private MapLook() {
	}

	/** The game's factor from the mouse sensitivity option (0 to 1) to raw pixels: {@code (s * 0.6 + 0.2)^3 * 8}. */
	public static double factor(double sensitivity) {
		double base = sensitivity * 0.6 + 0.2;
		return base * base * base * 8.0;
	}

	/** Degrees turned by a raw pixel movement of the mouse. */
	public static double degrees(double pixels, double factor) {
		return pixels * factor * DEGREES_PER_UNIT;
	}

	/** Whether a raw pixel movement is small enough to be the mouse's own. */
	public static boolean plausible(double pixels) {
		return Math.abs(pixels) <= MAX_PIXELS;
	}
}
