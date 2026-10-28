package com.davisr.thedreamtable;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.farm.FarmSharing;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.alert.FarmAlertMonitor;
import com.davisr.thedreamtable.network.CheckPlacementPayload;
import com.davisr.thedreamtable.network.DepositMaterialPayload;
import com.davisr.thedreamtable.network.FarmAreasPayload;
import com.davisr.thedreamtable.network.FarmActionResultPayload;
import com.davisr.thedreamtable.network.FarmAlertAckPayload;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.network.FarmCollaboratorPayload;
import com.davisr.thedreamtable.network.FarmCarePayload;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.FarmInvitePayload;
import com.davisr.thedreamtable.network.FarmInviteResponsePayload;
import com.davisr.thedreamtable.network.FarmPreferencePayload;
import com.davisr.thedreamtable.network.FarmRenamePayload;
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
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The loader-neutral setup, run by the loader's entrypoint right after it installs the platform. */
public final class TheDreamTableMod {
	public static final String MOD_ID = "the_dream_table";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private TheDreamTableMod() {
	}

	public static void init() {
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModScreenHandlers.register();
		FarmTemplates.load();
		DreamTableConfig.load();
		Platform.serverEvents().onServerStarted(server -> {
			server.getWorlds().forEach(FarmAreas::of);
			FarmLoader.of(server).migrateLegacyKeys(server);
		});
		Platform.serverEvents().onServerStarting(server -> {
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
		FarmActionResultPayload.register();
		FarmAlertsPayload.register();
		FarmAlertAckPayload.register();
		FarmRenamePayload.register();
		FarmInvitePayload.register();
		FarmInviteResponsePayload.register();
		FarmCollaboratorPayload.register();
		FarmPreferencePayload.register();
		FarmCarePayload.register();
		FarmLoader.register();
		FarmPreferences.register();
		FarmSharing.register();
		FarmAlertMonitor.register();
		LoadedFarmsPayload.register();
		ServerSchematicsPayload.register();
		FarmDetailsPayload.register();
		LOGGER.info("The Dream Table loaded");
	}
}
