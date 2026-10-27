package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Lists the farm materials; a swapped one shows its replacement and is marked as selected. */
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
			int y = ui.view().cardY(index);
			Item original = template.materials().get(index).item();
			Item replacement = ui.handler().materialItem(index);
			Item shown = replacement == null ? original : replacement;
			ui.card("material." + index, context, new ItemStack(shown), shown.getName(), y, mouseX, mouseY, shown != original, ui.handler().canSwap(index));
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), ui.view().listBackY(count), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		int count = ui.materialCount();
		for (int index = 0; index < count; index++) {
			if (!ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
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
		if (ui.buttonHit(mouseX, mouseY, ui.view().listBackY(count))) {
			ui.go(DreamTableScreenHandler.PAGE_WORK, DreamTableScreenHandler.BUTTON_MATERIALS_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return Viewport.listContentHeight(ui.materialCount());
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.materials");
	}
}
