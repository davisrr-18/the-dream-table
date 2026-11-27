package com.davisr.thedreamtable.client;

/** The pure decisions behind the capture mode's polled keys and timers, so they are tested without a client. */
final class KeyPresses {
	private KeyPresses() {
	}

	/** How many ticks a key has been down, given the count before this tick. */
	static int held(int previous, boolean down) {
		return down ? previous + 1 : 0;
	}

	/**
	 * Whether a key acts this tick: on its first tick down, or, for a key that repeats, every second tick once it has been held
	 * {@code repeatDelay} ticks. Never on the first tick after a screen was open: the screen's own key (the Enter that saved a
	 * name or sent a chat line) closes it before the tick, and would otherwise count as a new press in the game.
	 */
	static boolean acts(int held, boolean repeats, int repeatDelay, boolean screenWasOpen) {
		if (screenWasOpen) {
			return false;
		}
		return held == 1 || (repeats && held >= repeatDelay && (held - repeatDelay) % 2 == 0);
	}

	/** Whether a rescan is due: never done, the clock went back (a new world), or {@code interval} ticks have passed. */
	static boolean rescanDue(boolean scannedBefore, long now, long last, long interval) {
		return !scannedBefore || now < last || now - last >= interval;
	}
}
