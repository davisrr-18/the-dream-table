package com.davisr.thedreamtable.client;

/**
 * Keeps the Enter that closed a screen (the name screen, or the chat) from counting as a new press in the game. The game counts a
 * binding's presses, OS key repeats included, as soon as no screen is open, so a key still held when the screen closes would arrive as
 * fresh presses, on the next tick and for as long as it stays down. Pure, so it is tested without a client.
 */
final class ConfirmGuard {
	private boolean blocked;

	/**
	 * Whether the key's counted presses this tick are a real press. {@code screenOpen} is true while a screen is open and on the
	 * first tick after one was; {@code physicallyDown} is the key's real state, not the game's count. Call it every tick.
	 */
	boolean accept(int presses, boolean screenOpen, boolean physicallyDown) {
		if (!physicallyDown) {
			blocked = false;
		} else if (screenOpen) {
			// Down while a screen was in the way: it belongs to the screen until it is let go.
			blocked = true;
		}
		return presses > 0 && !blocked && !screenOpen;
	}
}
