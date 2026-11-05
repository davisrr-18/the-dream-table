package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

/** Installs the Forge implementations of the platform seams. */
final class ForgePlatform {
	private ForgePlatform() {
	}

	static ForgeServerNetwork install(IEventBus modBus) {
		ForgeServerNetwork network = new ForgeServerNetwork();
		Platform.install(new ForgePaths(), new ForgeBlockBreakGuard(), new ForgeBlockPlaceGuard(),
				network, new ForgeServerEvents(), new ForgeMenus(), new ForgeContent(modBus));
		return network;
	}

	static void installClient(IEventBus modBus, ForgeServerNetwork network) {
		Platform.installClient(new ForgeClientNetwork(network), new ForgeClientEvents(modBus), new ForgeClientRendering(modBus));
	}

	/** Names the event type outright: Forge's lambda type lookup cannot always resolve it. Canceled events are skipped. */
	static <T extends Event> void listen(IEventBus bus, Class<T> type, Consumer<T> listener) {
		bus.addListener(EventPriority.NORMAL, false, type, listener);
	}
}
