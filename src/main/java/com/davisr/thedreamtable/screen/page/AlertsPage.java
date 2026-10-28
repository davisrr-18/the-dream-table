package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.CARD_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_HEIGHT;

/** The selected farm's server-authorized alert history. Drawing a page acknowledges only its issued references. */
public final class AlertsPage implements TablePage {
	private final PageContext ui;
	private String acknowledged = "";

	public AlertsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensureAlerts();
		FarmAlertsPayload alerts = LoadedFarmsView.alerts();
		if (alerts == null) {
			context.drawText(ui.painter().text(), Text.translatable("gui.the_dream_table.alerts_waiting"), ui.view().cardX(), ui.view().cardY(0), 0x7B6B5A, false);
		} else if (alerts.entries().isEmpty()) {
			context.drawText(ui.painter().text(), Text.translatable("gui.the_dream_table.alerts_empty"), ui.view().cardX(), ui.view().cardY(0), 0x7B6B5A, false);
		} else {
			for (int index = 0; index < alerts.entries().size(); index++) {
				FarmAlertsPayload.Entry entry = alerts.entries().get(index);
				String key = "gui.the_dream_table.alert." + entry.type().toLowerCase(java.util.Locale.ROOT);
				ui.card("alert." + entry.eventId(), context, Items.BELL.getDefaultStack(), Text.translatable(key, entry.quantity()),
						ui.view().cardY(index), mouseX, mouseY, entry.active(), true);
			}
			if (!acknowledged.equals(alerts.token())) {
				LoadedFarmsView.acknowledgeAlerts(alerts);
				acknowledged = alerts.token();
			}
		}
		ui.button("back", context, Text.translatable("gui.the_dream_table.back"), ui.view().cardY(alerts == null ? 1 : alerts.entries().size() + 1), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		int row = Math.max(1, (LoadedFarmsView.alerts() == null ? 1 : LoadedFarmsView.alerts().entries().size() + 1));
		if (ui.buttonHit(mouseX, mouseY, ui.view().cardY(row))) {
			ui.go(DreamTableScreenHandler.PAGE_FARM, DreamTableScreenHandler.BUTTON_FARM_OPEN);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		FarmAlertsPayload alerts = LoadedFarmsView.alerts();
		int entries = alerts == null ? 0 : alerts.entries().size();
		return (entries + 2) * (CARD_HEIGHT + CARD_GAP);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.alerts");
	}
}
