package com.davisr.dreamtablepoc;

/** Server-side authorization for client increment requests, kept free of Minecraft types for unit tests. */
public final class PocRules {
	private PocRules() {
	}

	public static boolean canIncrement(boolean chunkLoaded, boolean targetIsCounterBlock, boolean menuOpenForTarget,
			boolean spectator, boolean withinReach) {
		return chunkLoaded && targetIsCounterBlock && menuOpenForTarget && !spectator && withinReach;
	}
}
