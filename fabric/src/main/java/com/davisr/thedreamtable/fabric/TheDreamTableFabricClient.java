package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.TheDreamTableClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric's client entrypoint: installs the client platform, then runs the loader-neutral client setup. */
public final class TheDreamTableFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricPlatform.installClient();
		FabricHandshakeClient.register();
		TheDreamTableClient.init();
	}
}
