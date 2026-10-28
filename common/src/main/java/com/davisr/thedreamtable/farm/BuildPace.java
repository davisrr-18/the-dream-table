package com.davisr.thedreamtable.farm;

/**
 * How fast a build goes, as a share of the server's fastest rate, from 1% to 100%: the Slow, Normal and Fast levels,
 * or any custom share. A pace is stored as {@code 1000 + percent}, which keeps it apart from what older saves stored:
 * minutes as negative numbers, which map to the closest level, and blocks per second as small positive numbers,
 * which fall back to {@link #DEFAULT}. Plain Java, so it runs on both sides.
 */
public final class BuildPace {
	private static final int PERCENT_BASE = 1000;
	public static final int MIN_PERCENT = 1;
	public static final int MAX_PERCENT = 100;
	public static final int SLOW = ofPercent(25);
	public static final int NORMAL = ofPercent(50);
	public static final int FAST = ofPercent(100);
	public static final int[] LEVELS = {SLOW, NORMAL, FAST};
	public static final int DEFAULT = NORMAL;
	/** The default fastest rate. Servers can change it in the config, between 1 and {@link #MAX_LIMIT}. */
	public static final int MAX_RATE = 256;
	public static final int MAX_LIMIT = 1024;
	public static final int TICKS_PER_SECOND = 20;

	private BuildPace() {
	}

	public static int ofPercent(int percent) {
		return PERCENT_BASE + Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
	}

	public static int percent(int pace) {
		return valid(pace) - PERCENT_BASE;
	}

	public static int valid(int pace) {
		if (pace >= PERCENT_BASE + MIN_PERCENT && pace <= PERCENT_BASE + MAX_PERCENT) {
			return pace;
		}
		return switch (pace) {
			case -10 -> SLOW;
			case -1 -> FAST;
			default -> DEFAULT;
		};
	}

	public static boolean isLevel(int pace) {
		for (int level : LEVELS) {
			if (level == pace) {
				return true;
			}
		}
		return false;
	}

	public static int clampMax(int maxRate) {
		return Math.max(1, Math.min(MAX_LIMIT, maxRate));
	}

	/** Blocks per second for this share of the server's fastest rate, never below 1. */
	public static int blocksPerSecond(int pace, int maxRate) {
		return Math.max(1, clampMax(maxRate) * percent(pace) / 100);
	}

	/** Seconds left for the remaining steps at this rate. Blocks already in place are skipped, so the real time can be shorter. */
	public static int secondsLeftAt(int blocksPerSecond, int totalSteps, int index) {
		int left = Math.max(0, totalSteps - index);
		int rate = Math.max(1, blocksPerSecond);
		return (left + rate - 1) / rate;
	}
}
