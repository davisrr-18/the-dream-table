package com.davisr.thedreamtable.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class DreamTableKeys {
	public static final KeyBinding ROTATE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.the_dream_table.rotate",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_R,
			"category.the_dream_table"
	));

	private DreamTableKeys() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.currentScreen != null) {
				return;
			}
			while (ROTATE.wasPressed()) {
				DreamTableHologram.rotate();
			}
		});
	}
}
