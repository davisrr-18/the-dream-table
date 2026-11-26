package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * Samples what each loaded finished farm's chests and barrels hold, for its production per hour. Like the alert
 * monitor it reads only chunks that are already loaded and never loads one; a farm that cannot be read is a gap, so its
 * time away is not counted. Each sample walks the farm's steps, so the cost grows with loaded farms only.
 * Follow-up: cache the container positions per farm instead of walking every step each time.
 */
public final class FarmProductionMonitor {
	private static final int INTERVAL_TICKS = 200;

	private FarmProductionMonitor() {
	}

	public static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			// Half an interval after the alert monitor, so the two storage scans never land on the same tick.
			if (server.getTicks() % INTERVAL_TICKS == INTERVAL_TICKS / 2) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTicks();
		for (ServerWorld world : server.getWorlds()) {
			FarmAreas areas = FarmAreas.of(world);
			for (FarmAreas.Finished farm : areas.finished()) {
				FarmTemplate layout = farm.layout();
				FarmStorage.Contents contents = layout == null || FarmStorage.status(world, layout, farm.site()) == FarmStorage.UNLOADED
						? null : FarmStorage.contents(world, layout, farm.site());
				if (contents == null) {
					areas.productionGap(farm.instanceId());
				} else {
					areas.sampleProduction(farm.instanceId(), snapshot(contents), now);
				}
			}
		}
	}

	static Map<String, Integer> snapshot(FarmStorage.Contents contents) {
		Map<String, Integer> items = new HashMap<>();
		for (ItemStack stack : contents.items()) {
			items.merge(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
		return items;
	}
}
