package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Draws the one farm the local player tracks, using only server-authorized list entries. */
public final class TrackedFarmHud {
	private TrackedFarmHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientSettings.showTrackedFarm() || client.options.hudHidden || client.player == null) {
			return;
		}
		FarmLoader.Entry farm = LoadedFarmsView.farms().stream().filter(FarmLoader.Entry::tracked).findFirst().orElse(null);
		if (farm == null) {
			return;
		}
		String name = farm.customName().isBlank() ? farm.farmId() : farm.customName();
		String here = client.player.getWorld().getRegistryKey().getValue().toString();
		String detail;
		if (!here.equals(farm.dimension())) {
			detail = farm.dimension();
		} else {
			int dx = farm.where().getX() - client.player.getBlockX();
			int dz = farm.where().getZ() - client.player.getBlockZ();
			int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
			detail = direction(dx, dz) + " · " + distance + " m";
		}
		Text text = Text.literal(name + " · " + detail);
		int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(text)) / 2;
		context.drawTextWithShadow(client.textRenderer, text, x, 28, 0xFFFFFF);
	}

	static String direction(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? "E" : "W";
		}
		return dz > 0 ? "S" : "N";
	}
}
