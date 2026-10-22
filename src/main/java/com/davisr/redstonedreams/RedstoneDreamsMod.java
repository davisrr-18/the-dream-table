package com.davisr.redstonedreams;

import com.davisr.redstonedreams.farm.FarmTemplates;
import com.davisr.redstonedreams.network.DepositMaterialPayload;
import com.davisr.redstonedreams.network.PlaceFarmPayload;
import com.davisr.redstonedreams.registry.ModBlockEntities;
import com.davisr.redstonedreams.registry.ModBlocks;
import com.davisr.redstonedreams.registry.ModItems;
import com.davisr.redstonedreams.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RedstoneDreamsMod implements ModInitializer {
	public static final String MOD_ID = "redstone_dreams";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModScreenHandlers.register();
		FarmTemplates.load();
		DepositMaterialPayload.register();
		PlaceFarmPayload.register();
		LOGGER.info("Redstone Dreams loaded");
	}
}
