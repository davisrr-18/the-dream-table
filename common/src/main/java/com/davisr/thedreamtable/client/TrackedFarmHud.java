package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.TrackedFarmPayload;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.page.LoadedFarmsPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Draws the one farm the local player tracks, using only what the server sent: its join message and then the My Farms
 * list. Forgotten when the player leaves the world, so another server never shows the previous one's farm.
 */
public final class TrackedFarmHud {
	/** Where the line sits when no notice is on screen. */
	private static final int BASE_Y = 28;
	private static final int NOTICE_GAP = 2;
	private static final int MARGIN = 8;
	private static TrackedFarmPayload tracked = TrackedFarmPayload.NONE;

	private TrackedFarmHud() {
	}

	public static void register() {
		Platform.clientNetwork().registerReceiver(TrackedFarmPayload.ID, payload -> tracked = payload);
		Platform.clientRendering().onHud(TrackedFarmHud::render);
	}

	/** Follows the list the server just sent: its tracked farm, or none when the list has none. */
	static void follow(List<FarmLoader.Entry> farms) {
		tracked = TrackedFarmPayload.of(farms.stream().filter(FarmLoader.Entry::tracked).findFirst().orElse(null));
	}

	static void clear() {
		tracked = TrackedFarmPayload.NONE;
	}

	static TrackedFarmPayload tracked() {
		return tracked;
	}

	private static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientSettings.showTrackedFarm() || client.options.hudHidden || client.player == null) {
			return;
		}
		TrackedFarmPayload farm = tracked;
		if (farm.isNone()) {
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
		Text text = Text.translatable("gui.the_dream_table.tracked_farm", LoadedFarmsPage.name(farm.customName(), farm.farmId()), detail);
		int screenWidth = context.getScaledWindowWidth();
		int y = lineY(TableNotices.bottom(client.textRenderer, screenWidth));
		// Wrapped to the window, so a long name never runs off the screen; each extra line stacks below.
		for (OrderedText line : client.textRenderer.wrapLines(text, Math.max(40, screenWidth - 2 * MARGIN))) {
			int x = (screenWidth - client.textRenderer.getWidth(line)) / 2;
			context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFFF);
			y += 10;
		}
	}

	/** The first row of the line: its usual place, or just under the table notice when that one reaches further down. */
	static int lineY(int noticeBottom) {
		return noticeBottom <= 0 ? BASE_Y : Math.max(BASE_Y, noticeBottom + NOTICE_GAP);
	}

	static String direction(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? "E" : "W";
		}
		return dz > 0 ? "S" : "N";
	}
}
