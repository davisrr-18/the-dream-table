package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.client.map.MapMode;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class DreamTableKeys {
	public static final KeyBinding ROTATE = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.rotate",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_R,
			"category.the_dream_table"
	));

	public static final KeyBinding MIRROR = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.mirror",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_M,
			"category.the_dream_table"
	));

	public static final KeyBinding LAYERS = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.layers",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_H,
			"category.the_dream_table"
	));

	public static final KeyBinding MAP = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.map",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_G,
			"category.the_dream_table"
	));

	public static final KeyBinding HOLOGRAM = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.hologram",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_J,
			"category.the_dream_table"
	));

	private DreamTableKeys() {
	}

	public static void register() {
		Platform.clientEvents().onTickEnd(client -> {
			if (client.currentScreen != null) {
				return;
			}
			while (ROTATE.wasPressed()) {
				DreamTableHologram.rotate();
			}
			while (MIRROR.wasPressed()) {
				DreamTableHologram.mirror();
			}
			while (LAYERS.wasPressed()) {
				DreamTableHologram.toggleLayers();
			}
			while (HOLOGRAM.wasPressed()) {
				DreamTableHologram.toggleGhosts();
			}
			while (MAP.wasPressed()) {
				MapMode.open(client);
			}
		});
		// Separate from the listener above, which skips ticks while a screen is open; the map runs its own tick.
		Platform.clientEvents().onTickEnd(MapMode::tick);
	}
}
