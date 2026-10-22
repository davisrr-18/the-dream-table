package com.davisr.redstonedreams.registry;

import com.davisr.redstonedreams.RedstoneDreamsMod;
import com.davisr.redstonedreams.screen.DreamTableScreenHandler;
import com.davisr.redstonedreams.screen.OpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<DreamTableScreenHandler> DREAM_TABLE = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(RedstoneDreamsMod.MOD_ID, "dream_table"),
			new ExtendedScreenHandlerType<>(DreamTableScreenHandler::new, OpenData.CODEC)
	);

	private ModScreenHandlers() {
	}

	public static void register() {
		RedstoneDreamsMod.LOGGER.debug("Registered screen handlers");
	}
}
