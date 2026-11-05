package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge's entrypoint: installs the platform, then runs the loader-neutral setup, and the client's on a client. */
@Mod(TheDreamTableMod.MOD_ID)
public final class TheDreamTableForge {
	public TheDreamTableForge(FMLJavaModLoadingContext context) {
		ForgeServerNetwork network = ForgePlatform.install(context.getModEventBus());
		TheDreamTableMod.init();
		// Every payload is registered by now; Forge needs the whole channel at once.
		network.build();
		if (FMLEnvironment.dist.isClient()) {
			TheDreamTableForgeClient.init(context.getModEventBus(), network);
		}
	}
}
