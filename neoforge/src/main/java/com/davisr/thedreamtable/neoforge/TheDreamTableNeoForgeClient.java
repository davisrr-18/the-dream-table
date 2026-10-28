package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.TheDreamTableClient;
import net.neoforged.bus.api.IEventBus;

/** The client half of the NeoForge entrypoint, kept apart so a dedicated server never loads client classes. */
final class TheDreamTableNeoForgeClient {
	private TheDreamTableNeoForgeClient() {
	}

	static void init(IEventBus modBus) {
		NeoForgePlatform.installClient(modBus);
		TheDreamTableClient.init();
	}
}
