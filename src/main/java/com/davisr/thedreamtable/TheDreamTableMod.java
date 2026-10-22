package com.davisr.thedreamtable;

import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.DepositMaterialPayload;
import com.davisr.thedreamtable.network.PlaceFarmPayload;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.registry.ModBlocks;
import com.davisr.thedreamtable.registry.ModItems;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
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
		DepositMaterialPayload.register();
		PlaceFarmPayload.register();
		LOGGER.info("The Dream Table loaded");
	}
}
