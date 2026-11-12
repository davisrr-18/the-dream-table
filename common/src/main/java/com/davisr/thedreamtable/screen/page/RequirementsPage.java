package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.WaterRow;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.RequirementsLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * One card per material with a real slot on its icon, where items can be dropped in, then the player inventory and the
 * two delivery buttons. The cards follow the screen like the other pages: one column on compact screens, two on wide ones.
 */
public final class RequirementsPage implements TablePage {
	private final PageContext ui;

	public RequirementsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
		TextRenderer text = ui.painter().text();
		int columns = columns();
		int count = ui.materialCount();
		handler.alignSlots(columns);
		int left = ui.view().slotOriginX();
		int top = ui.view().slotOriginY();
		FarmTemplate template = ui.template();
		for (int index = 0; index < count; index++) {
			MaterialSpec spec = template.materials().get(index);
			Slot slot = handler.getSlot(index);
			int have = WaterRow.covers(handler.getTablePos(), template, spec.item()) ? spec.count() : handler.supplied(index);
			boolean complete = have >= spec.count();
			Item shown = handler.materialItem(index);
			ItemStack required = new ItemStack(shown == null ? spec.item() : shown);
			Text amount = Text.translatable("gui.the_dream_table.count", have, spec.count());
			ui.painter().card(context, required, required.getName(), amount, left + RequirementsLayout.cardX(index, columns),
					top + RequirementsLayout.cardY(index, columns), CARD_WIDTH, false, complete, true);
			// An empty slot shows only the faded icon of what it wants, with no name; a filled one has the vanilla slot tip.
			if (slot.getStack().isEmpty() && handler.getCursorStack().isEmpty()) {
				ui.hoverItem(required, Text.translatable("gui.the_dream_table.item_supplied", amount).formatted(Formatting.GRAY),
						left + slot.x, top + slot.y, mouseX, mouseY);
			}
		}
		if (template != null && WaterRow.needed(template)) {
			TableSession table = WaterRow.sessionAt(handler.getTablePos());
			if (table != null) {
				boolean nearby = WaterRow.nearby(table, template);
				context.drawText(text, Text.translatable(WaterRow.key(nearby)), left + RequirementsLayout.columnX(0),
						top + RequirementsLayout.waterY(count, columns), nearby ? ACCENT : HINT, false);
			}
		}
		context.drawText(text, Text.translatable("gui.the_dream_table.inventory"), left + RequirementsLayout.inventoryX(columns),
				top + RequirementsLayout.inventoryLabelY(count, columns), HINT, false);
		for (int index = FarmTemplate.MAX_SLOTS; index < handler.slots.size(); index++) {
			Slot slot = handler.getSlot(index);
			ui.painter().slotFrame(context, left + slot.x, top + slot.y);
		}
		boolean creative = ui.actions().creative();
		drawDelivery(context, deliverAllY(), "deliver_all", "gui.the_dream_table.deliver_all", "gui.the_dream_table.deliver_all_hint",
				creative ? "gui.the_dream_table.deliver_creative" : null, mouseX, mouseY);
		drawDelivery(context, deliverNearbyY(), "deliver_nearby", "gui.the_dream_table.deliver_nearby", "gui.the_dream_table.deliver_nearby_tip",
				nearbyBlocker(creative), mouseX, mouseY);
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_WORK, DreamTableScreenHandler.BUTTON_BACK);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, deliverAllY())) {
			if (!ui.actions().creative()) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_DELIVER_ALL);
			}
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, deliverNearbyY())) {
			if (nearbyBlocker(ui.actions().creative()) == null) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_DELIVER_NEARBY);
			}
			return true;
		}
		return false;
	}

	/** Why Deliver From Nearby is off, or null: the server setting wins over this player's own switch. */
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

	/** A disabled button explains itself on its second line; an enabled one says what it does. */
	private void drawDelivery(DrawContext context, int y, String key, String label, String hint, String blocker, int mouseX, int mouseY) {
		ui.button(key, context, Text.translatable(label), Text.translatable(blocker == null ? hint : blocker), y, mouseX, mouseY, blocker == null, false);
	}

	private int columns() {
		return ui.view().columnCount();
	}

	private int deliverAllY() {
		return ui.view().slotOriginY() + RequirementsLayout.deliverAllY(ui.materialCount(), columns());
	}

	private int deliverNearbyY() {
		return ui.view().slotOriginY() + RequirementsLayout.deliverNearbyY(ui.materialCount(), columns());
	}

	private int backY() {
		return ui.view().slotOriginY() + RequirementsLayout.backY(ui.materialCount(), columns());
	}

	@Override
	public int contentHeight() {
		return Math.max(1, RequirementsLayout.contentHeight(ui.materialCount(), columns()));
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.requirements");
	}
}
