package com.davisr.dreamtablepoc.fabric;

import com.davisr.dreamtablepoc.client.DreamTablePocClient;
import net.fabricmc.api.ClientModInitializer;

public final class DreamTablePocFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DreamTablePocClient.init();
	}
}
