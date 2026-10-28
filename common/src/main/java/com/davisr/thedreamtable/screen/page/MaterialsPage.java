package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Lists the farm materials; a swapped one shows its replacement and is marked as selected. The delivery buttons sit under the list. */
public final class MaterialsPage implements TablePage {
	private final PageContext ui;

	public MaterialsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		FarmTemplate template = ui.template();
		int count = ui.materialCount();
		for (int index = 0; index < count; index++) {
			int x = cardX(ui.view(), index);
			int y = cardY(ui.view(), index);
			Item original = template.materials().get(index).item();
			Item replacement = ui.handler().materialItem(index);
			Item shown = replacement == null ? original : replacement;
			ui.cardAt("material." + index, context, new ItemStack(shown), shown.getName(), x, y, mouseX, mouseY, shown != original, ui.handler().canSwap(index));
		}
		boolean creative = ui.actions().creative();
		drawDelivery(context, deliverAllY(ui.view(), count), "deliver_all", "gui.the_dream_table.deliver_all", "gui.the_dream_table.deliver_all_hint",
				creative ? "gui.the_dream_table.deliver_creative" : null, mouseX, mouseY);
		drawDelivery(context, deliverNearbyY(ui.view(), count), "deliver_nearby", "gui.the_dream_table.deliver_nearby", "gui.the_dream_table.deliver_nearby_tip",
				nearbyBlocker(creative), mouseX, mouseY);
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(ui.view(), count), mouseX, mouseY, true, false);
	}

	/** Why Deliver from Nearby is off, or null: the server setting wins over this player's own switch. */
	private String nearbyBlocker(boolean creative) {
		return nearbyBlocker(creative, ui.handler().nearbyDeliveryAllowed(), ClientSettings.deliverFromNearby());
	}

	static String nearbyBlocker(boolean creative, boolean serverAllows, boolean playerWants) {
		if (creative) {
			return "gui.the_dream_table.deliver_creative";
		}
		if (!serverAllows) {
			return "gui.the_dream_table.deliver_nearby_off_server";
		}
		return playerWants ? null : "gui.the_dream_table.deliver_nearby_off_player";
	}

	/** A disabled button explains itself in a tooltip; an enabled one says what it does. */
	private void drawDelivery(DrawContext context, int y, String key, String label, String hint, String blocker, int mouseX, int mouseY) {
		Text text = Text.translatable(label);
		ui.button(key, context, text, y, mouseX, mouseY, blocker == null, false);
		if (ui.buttonHit(mouseX, mouseY, y)) {
			ui.tooltip(text, Text.translatable(blocker == null ? hint : blocker));
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		int count = ui.materialCount();
		for (int index = 0; index < count; index++) {
			if (!ui.cardHit(mouseX, mouseY, cardX(ui.view(), index), cardY(ui.view(), index))) {
				continue;
			}
			if (ui.handler().canSwap(index)) {
				ui.handler().previewSwap(index);
				ui.go(DreamTableScreenHandler.PAGE_SWAP, DreamTableScreenHandler.BUTTON_SWAP_OPEN + index);
			} else if (!ui.handler().swapOptions(index).isEmpty()) {
				ui.message("gui.the_dream_table.swap_locked");
			}
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, deliverAllY(ui.view(), count))) {
			if (!ui.actions().creative()) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_DELIVER_ALL);
			}
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, deliverNearbyY(ui.view(), count))) {
			if (nearbyBlocker(ui.actions().creative()) == null) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_DELIVER_NEARBY);
			}
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, backY(ui.view(), count))) {
			ui.go(DreamTableScreenHandler.PAGE_WORK, DreamTableScreenHandler.BUTTON_MATERIALS_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return contentHeight(ui.view(), ui.materialCount());
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.materials");
	}

	static int cardX(Viewport view, int index) {
		return view.gridCardX(index);
	}

	static int cardY(Viewport view, int index) {
		return view.gridCardY(index);
	}

	static int deliverAllY(Viewport view, int count) {
		return count == 0 ? cardY(view, 0) + 10 : cardY(view, count - 1) + CARD_HEIGHT + 10;
	}

	static int deliverNearbyY(Viewport view, int count) {
		return deliverAllY(view, count) + BUTTON_HEIGHT + BUTTON_GAP;
	}

	static int backY(Viewport view, int count) {
		return deliverNearbyY(view, count) + BUTTON_HEIGHT + BUTTON_GAP;
	}

	static int contentHeight(Viewport view, int count) {
		return backY(view, count) - cardY(view, 0) + PAD + BUTTON_HEIGHT + 8;
	}
}
