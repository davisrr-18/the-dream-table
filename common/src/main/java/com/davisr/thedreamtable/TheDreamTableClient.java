package com.davisr.thedreamtable;

import com.davisr.thedreamtable.client.BuildProgressLine;
import com.davisr.thedreamtable.client.TableNotices;
import com.davisr.thedreamtable.client.ClientLifecycle;
import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import com.davisr.thedreamtable.client.FarmFaces;
import com.davisr.thedreamtable.client.FarmMiniature;
import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.client.OtherFarms;
import com.davisr.thedreamtable.client.OwnSessions;
import com.davisr.thedreamtable.client.ServerSchematics;
import com.davisr.thedreamtable.client.TrackedFarmHud;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import com.davisr.thedreamtable.screen.DreamTableScreen;

/** The loader-neutral client setup, run by the loader's client entrypoint right after it installs the client platform. */
public final class TheDreamTableClient {
	private TheDreamTableClient() {
	}

	public static void init() {
		Platform.clientRendering().registerScreen(ModScreenHandlers.DREAM_TABLE, DreamTableScreen::new);
		Platform.clientRendering().registerBlockEntityRenderer(ModBlockEntities.DREAM_TABLE, FarmMiniature::new);
		DreamTableKeys.register();
		DreamTableHologram.register();
		DreamTableHologram.registerClicks();
		DreamTableHologram.registerChecks();
		ClientSettings.load();
		ClientLifecycle.register();
		OwnSessions.register();
		OtherFarms.register();
		TrackedFarmHud.register();
		TableNotices.register();
		BuildProgressLine.register();
		LoadedFarmsView.register();
		FarmFaces.register();
		ServerSchematics.register();
		TheDreamTableMod.LOGGER.info("The Dream Table client loaded");
	}
}
