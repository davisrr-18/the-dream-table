package com.davisr.thedreamtable;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/** Loads the vanilla registries once, so tests can use blocks and items. */
public final class GameSetup {
	private static boolean ready;

	private GameSetup() {
	}

	public static synchronized void bootstrap() {
		if (!ready) {
			SharedConstants.createGameVersion();
			Bootstrap.initialize();
			ready = true;
		}
	}
}
