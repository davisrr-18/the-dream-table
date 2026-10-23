package com.davisr.thedreamtable.farm;

/**
 * How fast a build goes, as a target duration: "finish in about N minutes". The rate comes from the farm's size,
 * so small and large farms take about the same time. A pace is stored as minus the minutes, which keeps old saves,
 * whose positive blocks-per-second values are no longer offered, falling back to {@link #DEFAULT}.
 * Plain Java, so it runs on both sides.
 */
public final class BuildPace {
	public static final int[] MINUTES = {10, 5, 1};
	public static final int DEFAULT = ofMinutes(5);
	/** The fastest a build may go, so a huge farm on the 1 minute pace can't flood the server. */
	public static final int MAX_RATE = 256;
	public static final int TICKS_PER_SECOND = 20;

	private BuildPace() {
	}

	public static int valid(int pace) {
		for (int minutes : MINUTES) {
			if (pace == ofMinutes(minutes)) {
				return pace;
			}
		}
		return DEFAULT;
	}

	public static int ofMinutes(int minutes) {
		return -minutes;
	}

	/** Blocks per second that spread the whole farm over the chosen time, never slower than 1 or faster than {@link #MAX_RATE}. */
	public static int blocksPerSecond(int pace, int totalSteps) {
		int seconds = -valid(pace) * 60;
		int rate = (Math.max(0, totalSteps) + seconds - 1) / seconds;
		return Math.max(1, Math.min(MAX_RATE, rate));
	}

	/** Seconds left for the remaining steps at this pace. Blocks already in place are skipped, so the real time can be shorter. */
	public static int secondsLeft(int pace, int totalSteps, int index) {
		int left = Math.max(0, totalSteps - index);
		int rate = blocksPerSecond(pace, totalSteps);
		return (left + rate - 1) / rate;
	}
}
