package com.davisr.thedreamtable.farm.capture;

/**
 * Who may capture: operators (game master permission, as {@code FarmProtection} reads it) and the host of a single-player or
 * LAN world. The server decides; the client uses the same rule to show the control.
 */
public final class CaptureAccess {
	private CaptureAccess() {
	}

	public static boolean allowed(boolean operator, boolean host) {
		return operator || host;
	}
}
