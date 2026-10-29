package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.fabricmc.api.ModInitializer;

/** Fabric's common entrypoint: installs the platform, then runs the loader-neutral setup. */
public final class TheDreamTableFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		FabricPlatform.install();
		FabricHandshake.register();
		TheDreamTableMod.init();
	}
}
