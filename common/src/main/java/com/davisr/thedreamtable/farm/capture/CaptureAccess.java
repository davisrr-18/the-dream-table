package com.davisr.thedreamtable.farm.capture;

/** Who may capture: operators and the host of a single-player or LAN world. The server decides; the client uses the same rule to show the control. */
public final class CaptureAccess {
	/** The vanilla operator level, like {@code FarmProtection.OPERATOR_LEVEL}. */
	public static final int OPERATOR_LEVEL = 2;

	private CaptureAccess() {
	}

	public static boolean allowed(boolean operator, boolean host) {
		return operator || host;
	}
}
