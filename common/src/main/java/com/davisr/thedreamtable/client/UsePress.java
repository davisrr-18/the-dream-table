package com.davisr.thedreamtable.client;

/**
 * Tells a new press of the use key from the repeats of a held one, and makes sure one press is recorded once whichever way it is
 * seen. The game reaches a click two ways: its use events (a block in reach, or an item in hand) and, for an empty hand aimed at
 * air or a block past its reach, no event at all, which only the key's state at the end of the tick shows. Pure, so it is tested
 * without a client.
 */
final class UsePress {
	private boolean wasDown;
	private boolean handled;

	/** A use event fired: true when it starts a new press that nothing has recorded yet. A held key's repeated uses are not new. */
	boolean onUseEvent() {
		if (wasDown || handled) {
			return false;
		}
		handled = true;
		return true;
	}

	/** The end of a tick, with the key's state: true when a new press began that no use event recorded. Call it every tick, active or not. */
	boolean onTick(boolean down) {
		boolean fresh = down && !wasDown && !handled;
		if (fresh) {
			handled = true;
		}
		if (!down) {
			handled = false;
		}
		wasDown = down;
		return fresh;
	}
}
