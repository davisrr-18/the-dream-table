package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/** Installs the Forge implementations of the platform seams. Forge 61 events carry their own static {@code BUS}. */
final class ForgePlatform {
	private ForgePlatform() {
	}

	static ForgeServerNetwork install(BusGroup modBus) {
		ForgeServerNetwork network = new ForgeServerNetwork();
		Platform.install(new ForgePaths(), new ForgeBlockBreakGuard(), new ForgeBlockPlaceGuard(),
				network, new ForgeServerEvents(), new ForgeMenus(), new ForgeContent(modBus));
		Platform.installModInfo(new ForgeModInfo());
		return network;
	}

	static void installClient(BusGroup modBus, ForgeServerNetwork network) {
		Platform.installClient(new ForgeClientNetwork(network), new ForgeClientEvents(), new ForgeClientRendering(modBus));
	}
}
