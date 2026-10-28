package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge's entrypoint: installs the platform, then runs the loader-neutral setup, and the client's on a client. */
@Mod(TheDreamTableMod.MOD_ID)
public final class TheDreamTableNeoForge {
	public TheDreamTableNeoForge(IEventBus modBus, Dist dist) {
		NeoForgePlatform.install(modBus);
		TheDreamTableMod.init();
		if (dist.isClient()) {
			TheDreamTableNeoForgeClient.init(modBus);
		}
	}
}
