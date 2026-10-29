package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.screen.ui.Theme;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** How My Farms shows alerts: a red warning sign before the farm's name in the list, and red lines last in its details. */
public final class AlertText {
	/** U+26A0, drawn by the vanilla Unifont fallback. */
	public static final String SIGN = "⚠";
	private static final Style RED = Style.EMPTY.withColor(Theme.ERROR & 0xFFFFFF);

	private AlertText() {
	}

	/** The farm's list name, led by the warning sign when it has an active alert. */
	public static Text listName(FarmLoader.Entry farm, Text name) {
		return farm.alerts() > 0 ? Text.literal(SIGN + " ").setStyle(RED).append(name) : name;
	}

	/** "⚠ Alert: 12 blocks missing", in red. */
	public static Text line(String type, int quantity) {
		return Text.translatable("gui.the_dream_table.alert_line",
				Text.translatable("gui.the_dream_table.alert." + type.toLowerCase(Locale.ROOT), quantity)).setStyle(RED);
	}

	/** One line per active alert in the page the server sent for this farm; none while the farm has no active alert. */
	public static List<Text> lines(FarmLoader.Entry farm, FarmAlertsPayload alerts) {
		if (farm.alerts() <= 0 || alerts == null || !alerts.key().equals(farm.key())) {
			return List.of();
		}
		return alerts.entries().stream().filter(FarmAlertsPayload.Entry::active).map(entry -> (Text) line(entry.type(), entry.quantity())).toList();
	}
}
