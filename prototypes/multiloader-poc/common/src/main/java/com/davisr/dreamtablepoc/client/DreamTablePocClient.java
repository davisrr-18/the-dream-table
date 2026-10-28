package com.davisr.dreamtablepoc.client;

import com.davisr.dreamtablepoc.CounterMenu;
import com.davisr.dreamtablepoc.PocContent;
import com.davisr.dreamtablepoc.network.CounterSyncPayload;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class DreamTablePocClient {
	private DreamTablePocClient() {
	}

	public static void init() {
		// On NeoForge Architectury queues this on its mod bus for RegisterMenuScreensEvent, which fires before client setup.
		MenuRegistry.registerScreenFactory(PocContent.COUNTER_MENU_TYPE, CounterScreen::new);

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, CounterSyncPayload.ID, CounterSyncPayload.CODEC,
				(payload, context) -> context.queue(() -> {
					MinecraftClient client = MinecraftClient.getInstance();
					if (client.player == null) {
						return;
					}
					if (client.player.currentScreenHandler instanceof CounterMenu menu) {
						menu.setCounter(payload.value());
					} else {
						client.player.sendMessage(Text.literal("[poc] counter=" + payload.value()), true);
					}
				}));
	}
}
