package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

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
			ui.collectionNote(context, Text.translatable("gui.the_dream_table.alerts_waiting"), ui.view().cardY(0), 0x7B6B5A);
		} else if (alerts.entries().isEmpty()) {
			ui.collectionNote(context, Text.translatable("gui.the_dream_table.alerts_empty"), ui.view().cardY(0), 0x7B6B5A);
		} else {
			for (int index = 0; index < alerts.entries().size(); index++) {
				FarmAlertsPayload.Entry entry = alerts.entries().get(index);
				String key = "gui.the_dream_table.alert." + entry.type().toLowerCase(java.util.Locale.ROOT);
				ui.cardAt("alert." + entry.eventId(), context, Items.BELL.getDefaultStack(), Text.translatable(key, entry.quantity()),
						cardX(ui.view(), index), cardY(ui.view(), index), mouseX, mouseY, entry.active(), true);
			}
			if (!acknowledged.equals(alerts.token())) {
				LoadedFarmsView.acknowledgeAlerts(alerts);
				acknowledged = alerts.token();
			}
		}
		ui.button("back", context, Text.translatable("gui.the_dream_table.back"), backY(ui.view(), alerts == null ? 0 : alerts.entries().size()), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		int entries = LoadedFarmsView.alerts() == null ? 0 : LoadedFarmsView.alerts().entries().size();
		if (ui.buttonHit(mouseX, mouseY, backY(ui.view(), entries))) {
			ui.go(DreamTableScreenHandler.PAGE_FARM, DreamTableScreenHandler.BUTTON_FARM_OPEN);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		FarmAlertsPayload alerts = LoadedFarmsView.alerts();
		int entries = alerts == null ? 0 : alerts.entries().size();
		return contentHeight(ui.view(), entries);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.alerts");
	}

	static int cardX(Viewport view, int index) {
		return view.gridCardX(index);
	}

	static int cardY(Viewport view, int index) {
		return view.gridCardY(index);
	}

	static int backY(Viewport view, int count) {
		return count == 0 ? cardY(view, 0) + 10 : cardY(view, count - 1) + CARD_HEIGHT + 10;
	}

	static int contentHeight(Viewport view, int count) {
		return backY(view, count) - cardY(view, 0) + PAD + BUTTON_HEIGHT;
	}
}
