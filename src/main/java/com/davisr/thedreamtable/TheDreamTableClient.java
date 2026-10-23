package com.davisr.thedreamtable;

import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.DreamTableClipboard;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import com.davisr.thedreamtable.client.OtherFarms;
import com.davisr.thedreamtable.client.OwnSessions;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import com.davisr.thedreamtable.screen.DreamTableScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public final class TheDreamTableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.DREAM_TABLE, DreamTableScreen::new);
		DreamTableKeys.register();
		DreamTableHologram.register();
		DreamTableClipboard.register();
		DreamTableHologram.registerClicks();
		DreamTableHologram.registerChecks();
		ClientSettings.load();
		OwnSessions.register();
		OtherFarms.register();
		TheDreamTableMod.LOGGER.info("The Dream Table client loaded");
	}
}
