package com.davisr.thedreamtable.client.map;

/**
 * Keeps the Enter that closed the name screen from opening it again. A held key keeps sending repeats, and a screen receives each
 * one as a press, so once the name screen is gone the same hold would arrive at the map as fresh presses for as long as it stays
 * down. Pure, so it is tested without a client.
 */
final class ConfirmGuard {
	private boolean blocked;

	/** The name screen just closed while the key was (or was not) physically down: a key still down belongs to that screen until it is let go. */
	void screenClosed(boolean physicallyDown) {
		blocked = physicallyDown;
	}

	/** Whether a press of the key arriving now is a real one. {@code physicallyDown} is the key's real state, not the game's count. */
	boolean accept(boolean physicallyDown) {
		if (!physicallyDown) {
			blocked = false;
		}
		return !blocked;
	}
}
