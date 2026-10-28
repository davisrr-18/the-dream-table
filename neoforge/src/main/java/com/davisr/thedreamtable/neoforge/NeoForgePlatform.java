package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.Platform;
import net.neoforged.bus.api.IEventBus;

/** Installs the NeoForge implementations of the platform seams. */
final class NeoForgePlatform {
	private NeoForgePlatform() {
	}

	static void install(IEventBus modBus) {
		Platform.install(new NeoForgePaths(), new NeoForgeBlockBreakGuard(), new NeoForgeBlockPlaceGuard(),
				new NeoForgeServerNetwork(modBus), new NeoForgeServerEvents(), new NeoForgeMenus(), new NeoForgeContent(modBus));
	}

	static void installClient(IEventBus modBus) {
		Platform.installClient(new NeoForgeClientNetwork(), new NeoForgeClientEvents(modBus), new NeoForgeClientRendering(modBus));
	}
}
