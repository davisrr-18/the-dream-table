package com.davisr.redstonedreams;

import com.davisr.redstonedreams.client.DreamTableClipboard;
import com.davisr.redstonedreams.client.DreamTableHologram;
import com.davisr.redstonedreams.client.DreamTableKeys;
import com.davisr.redstonedreams.registry.ModScreenHandlers;
import com.davisr.redstonedreams.screen.DreamTableScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public final class RedstoneDreamsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.DREAM_TABLE, DreamTableScreen::new);
		DreamTableKeys.register();
		DreamTableHologram.register();
		DreamTableClipboard.register();
		DreamTableHologram.registerClicks();
		RedstoneDreamsMod.LOGGER.info("Redstone Dreams client loaded");
	}
}
