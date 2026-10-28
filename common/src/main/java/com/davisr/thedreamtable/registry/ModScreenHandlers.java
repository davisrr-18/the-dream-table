package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.OpenData;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public final class ModScreenHandlers {
	public static final Supplier<ScreenHandlerType<DreamTableScreenHandler>> DREAM_TABLE = Platform.content().register(
			RegistryKeys.SCREEN_HANDLER,
			Identifier.of(TheDreamTableMod.MOD_ID, "dream_table"),
			() -> Platform.menus().extendedType(DreamTableScreenHandler::new, OpenData.CODEC)
	);

	private ModScreenHandlers() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered screen handlers");
	}
}
