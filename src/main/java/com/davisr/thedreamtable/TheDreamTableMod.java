package com.davisr.thedreamtable;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.CheckPlacementPayload;
import com.davisr.thedreamtable.network.DepositMaterialPayload;
import com.davisr.thedreamtable.network.FarmAreasPayload;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import com.davisr.thedreamtable.network.PlaceFarmPayload;
import com.davisr.thedreamtable.network.PlacementResultPayload;
import com.davisr.thedreamtable.network.RequestSessionPayload;
import com.davisr.thedreamtable.network.ServerSchematicsPayload;
import com.davisr.thedreamtable.network.SessionSyncPayload;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.registry.ModBlocks;
import com.davisr.thedreamtable.registry.ModItems;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheDreamTableMod implements ModInitializer {
	public static final String MOD_ID = "the_dream_table";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModScreenHandlers.register();
		FarmTemplates.load();
		DreamTableConfig.load();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getWorlds().forEach(FarmAreas::of));
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DreamTableConfig.load();
			FarmTemplates.reloadImports(server.getSavePath(WorldSavePath.ROOT));
		});
		DepositMaterialPayload.register();
		PlaceFarmPayload.register();
		CheckPlacementPayload.register();
		PlacementResultPayload.register();
		SessionSyncPayload.register();
		RequestSessionPayload.register();
		FarmAreasPayload.register();
		FarmLoader.register();
		LoadedFarmsPayload.register();
		ServerSchematicsPayload.register();
		FarmDetailsPayload.register();
		LOGGER.info("The Dream Table loaded");
	}
}
