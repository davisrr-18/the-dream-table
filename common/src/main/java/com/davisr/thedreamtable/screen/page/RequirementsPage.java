package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.WaterRow;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.screen.DreamTableLayout;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The slot grid where materials can be dropped in, with the player inventory and the two delivery buttons below. */
public final class RequirementsPage implements TablePage {
	private final PageContext ui;

	public RequirementsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
		handler.alignInventory(ui.materialCount());
		TextRenderer text = ui.painter().text();
		int left = ui.view().slotOriginX();
		int top = ui.view().slotOriginY();
		FarmTemplate template = ui.template();
		int rows = template == null ? 0 : template.materials().size();
		for (int index = 0; index < rows; index++) {
			MaterialSpec spec = template.materials().get(index);
			Slot slot = handler.getSlot(index);
			int slotX = left + slot.x;
			int slotY = top + slot.y;
			int have = WaterRow.covers(handler.getTablePos(), template, spec.item()) ? spec.count() : handler.supplied(index);
			boolean complete = have >= spec.count();
			ui.painter().slotFrame(context, slotX, slotY);
			Item shown = handler.materialItem(index);
			ItemStack required = new ItemStack(shown == null ? spec.item() : shown);
			context.drawItem(required, slotX, slotY);
			if (complete) {
				context.drawBorder(slotX - 1, slotY - 1, 18, 18, ACCENT);
			}
			Text count = Text.translatable("gui.the_dream_table.count", have, spec.count());
			// An empty slot shows only the faded icon of what it wants, with no name; a filled one has the vanilla slot tip.
			if (slot.getStack().isEmpty() && ui.handler().getCursorStack().isEmpty()) {
				ui.hoverItem(required, Text.translatable("gui.the_dream_table.item_supplied", count).formatted(Formatting.GRAY), slotX, slotY, mouseX, mouseY);
			}
			context.drawText(text, count, slotX + 8 - text.getWidth(count) / 2, slotY + 16, complete ? ACCENT : HINT, false);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
		boolean creative = ui.actions().creative();
		drawDelivery(context, deliverAllY(), "deliver_all", "gui.the_dream_table.deliver_all", "gui.the_dream_table.deliver_all_hint",
				creative ? "gui.the_dream_table.deliver_creative" : null, mouseX, mouseY);
		drawDelivery(context, deliverNearbyY(), "deliver_nearby", "gui.the_dream_table.deliver_nearby", "gui.the_dream_table.deliver_nearby_tip",
				nearbyBlocker(creative), mouseX, mouseY);
		if (template != null && WaterRow.needed(template)) {
			TableSession table = WaterRow.sessionAt(handler.getTablePos());
			if (table != null) {
				boolean nearby = WaterRow.nearby(table, template);
				// Below the player inventory, where requirementsHeight leaves room; water is no slot, so it has no grid position.
				context.drawText(text, Text.translatable(WaterRow.key(nearby)), left + 16, top + DreamTableLayout.inventoryY(ui.materialCount()) + 78,
						nearby ? ACCENT : HINT, false);
			}
		}

		context.drawText(text, Text.translatable("gui.the_dream_table.inventory"), left + 16, top + DreamTableLayout.inventoryY(ui.materialCount()) - 12, HINT, false);
		for (int index = FarmTemplate.MAX_SLOTS; index < handler.slots.size(); index++) {
			Slot slot = handler.getSlot(index);
			ui.painter().slotFrame(context, left + slot.x, top + slot.y);
		}
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

	private int deliverAllY() {
		return ui.view().slotOriginY() + DreamTableLayout.deliverAllY(ui.materialCount());
	}

	private int deliverNearbyY() {
		return ui.view().slotOriginY() + DreamTableLayout.deliverNearbyY(ui.materialCount());
	}

	@Override
	public int contentHeight() {
		return Math.max(1, DreamTableLayout.requirementsHeight(ui.materialCount()) - HEADER);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.requirements");
	}

	private int backY() {
		return ui.view().slotOriginY() + DreamTableLayout.backY(ui.materialCount());
	}
}
