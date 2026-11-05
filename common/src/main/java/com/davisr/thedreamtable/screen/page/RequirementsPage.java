package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.clipboard.WaterRow;
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

/** The slot grid where materials can be dropped in, with the player inventory below. */
public final class RequirementsPage implements TablePage {
	private final PageContext ui;

	public RequirementsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
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
		return false;
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
