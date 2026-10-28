package com.davisr.thedreamtable.screen.page;

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
			int have = handler.supplied(index);
			boolean complete = have >= spec.count();
			ui.painter().slotFrame(context, slotX, slotY);
			Item shown = handler.materialItem(index);
			context.drawItem(new ItemStack(shown == null ? spec.item() : shown), slotX, slotY);
			if (complete) {
				context.drawBorder(slotX - 1, slotY - 1, 18, 18, ACCENT);
			}
			Text count = Text.translatable("gui.the_dream_table.count", have, spec.count());
			context.drawText(text, count, slotX + 8 - text.getWidth(count) / 2, slotY + 16, complete ? ACCENT : HINT, false);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);

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
