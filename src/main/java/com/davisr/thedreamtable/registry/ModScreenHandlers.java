package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.OpenData;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<DreamTableScreenHandler> DREAM_TABLE = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(TheDreamTableMod.MOD_ID, "dream_table"),
			Platform.menus().extendedType(DreamTableScreenHandler::new, OpenData.CODEC)
	);

	private ModScreenHandlers() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered screen handlers");
	}
}
