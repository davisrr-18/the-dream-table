package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableClient;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/** The client half of the Forge entrypoint, kept apart so a dedicated server never loads client classes. */
final class TheDreamTableForgeClient {
	private TheDreamTableForgeClient() {
	}

	static void init(BusGroup modBus, ForgeServerNetwork network) {
		ForgePlatform.installClient(modBus, network);
		TheDreamTableClient.init();
	}
}
