package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.page.LoadedFarmsPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

/** Draws the one farm the local player tracks, using only server-authorized list entries. */
public final class TrackedFarmHud {
	private TrackedFarmHud() {
	}

	public static void register() {
		Platform.clientRendering().onHud(TrackedFarmHud::render);
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
		String here = client.player.getWorld().getRegistryKey().getValue().toString();
		Text detail;
		if (!here.equals(farm.dimension())) {
			detail = LoadedFarmsPage.dimension(farm.dimension());
		} else {
			int dx = farm.where().getX() - client.player.getBlockX();
			int dz = farm.where().getZ() - client.player.getBlockZ();
			int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
			Text heading = Text.translatable("gui.the_dream_table.direction." + direction(dx, dz).toLowerCase(Locale.ROOT));
			detail = Text.translatable("gui.the_dream_table.tracked_distance", heading, distance);
		}
		Text text = Text.translatable("gui.the_dream_table.tracked_farm", LoadedFarmsPage.name(farm), detail);
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
