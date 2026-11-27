package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.client.map.MapMode;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

public final class DreamTableKeys {
	// 1.21.9+ key bindings take a registered category; its label is the key `key.category.the_dream_table.main`.
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(TheDreamTableMod.MOD_ID, "main"));

	public static final KeyBinding ROTATE = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.rotate",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_R,
			CATEGORY
	));

	public static final KeyBinding MIRROR = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.mirror",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_M,
			CATEGORY
	));

	public static final KeyBinding LAYERS = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.layers",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_H,
			CATEGORY
	));

	public static final KeyBinding PLACING = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.placing",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_P,
			CATEGORY
	));

	public static final KeyBinding MAP = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.map",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_G,
			CATEGORY
	));

	/** For operators and the host of a single-player world: starts and ends picking an area of the world to capture as a schematic. */
	public static final KeyBinding CAPTURE = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.capture",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_K,
			CATEGORY
	));

	public static final KeyBinding HOLOGRAM = Platform.clientEvents().registerKeyBinding(new KeyBinding(
			"key.the_dream_table.hologram",
			InputUtil.Type.KEYSYM,
			InputUtil.GLFW_KEY_J,
			CATEGORY
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
			while (PLACING.wasPressed()) {
				DreamTableHologram.togglePlacing();
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
			while (CAPTURE.wasPressed()) {
				CaptureMode.toggle(client);
			}
		});
		// Separate from the listener above, which skips ticks while a screen is open; the map runs its own tick.
		Platform.clientEvents().onTickEnd(MapMode::tick);
	}
}
