package com.davisr.thedreamtable.farm.alert;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/** Translates loaded-world farm health observations into stable alert transitions. */
public final class FarmAlertMonitor {
	private static final int INTERVAL_TICKS = 100;
	public record Observation(boolean loaded, boolean designAvailable, int storageStatus, int missingBlocks) {
	}

	private FarmAlertMonitor() {
	}

	/** Polls existing chunks only; alerts never cause a farm or its storage to load. */
	public static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			if (server.getTicks() % INTERVAL_TICKS == 0) {
				tick(server);
			}
		});
	}

	static void tick(MinecraftServer server) {
		long time = System.currentTimeMillis();
		for (ServerWorld world : server.getWorlds()) {
			FarmAreas areas = FarmAreas.of(world);
			for (FarmAreas.Finished farm : areas.finished()) {
				FarmTemplate layout = farm.layout();
				if (layout == null) {
					areas.observeAlert(farm.instanceId(), FarmAlertType.DESIGN_UNAVAILABLE, 1, time);
					continue;
				}
				areas.observeAlert(farm.instanceId(), FarmAlertType.DESIGN_UNAVAILABLE, 0, time);
				FarmAreas.Presence presence = FarmAreas.presence(world, layout, farm.site(), farm.swaps());
				int storage = FarmStorage.status(world, layout, farm.site());
				if (presence.unloaded() || storage == FarmStorage.UNLOADED) {
					continue;
				}
				areas.observeAlert(farm.instanceId(), FarmAlertType.STORAGE_FULL, storage == FarmStorage.FULL ? 1 : 0, time);
				areas.observeAlert(farm.instanceId(), FarmAlertType.BLOCKS_MISSING, presence.missing(), time);
			}
		}
	}

	/** Never resolves a world-derived problem while its chunks are unavailable. */
	public static void observe(FarmAlerts alerts, Observation observation, long time) {
		if (!observation.designAvailable()) {
			alerts.observe(FarmAlertType.DESIGN_UNAVAILABLE, 1, time);
			return;
		}
		alerts.observe(FarmAlertType.DESIGN_UNAVAILABLE, 0, time);
		if (!observation.loaded()) {
			alerts.unknown(FarmAlertType.STORAGE_FULL);
			alerts.unknown(FarmAlertType.BLOCKS_MISSING);
			return;
		}
		alerts.observe(FarmAlertType.STORAGE_FULL, observation.storageStatus() == FarmStorage.FULL ? 1 : 0, time);
		alerts.observe(FarmAlertType.BLOCKS_MISSING, observation.missingBlocks() == FarmLoader.UNKNOWN ? 0 : observation.missingBlocks(), time);
	}
}
