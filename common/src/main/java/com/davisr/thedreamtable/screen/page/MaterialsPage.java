package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

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
			int x = cardX(ui.view(), index);
			int y = cardY(ui.view(), index);
			Item original = template.materials().get(index).item();
			Item replacement = ui.handler().materialItem(index);
			Item shown = replacement == null ? original : replacement;
			ui.cardAt("material." + index, context, new ItemStack(shown), shown.getName(), x, y, mouseX, mouseY, shown != original, ui.handler().canSwap(index));
		}
		ui.backButton(context, backY(ui.view(), count), mouseX, mouseY);
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
		if (ui.backHit(mouseX, mouseY, backY(ui.view(), count))) {
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

	static int backY(Viewport view, int count) {
		return count == 0 ? cardY(view, 0) + 10 : cardY(view, count - 1) + CARD_HEIGHT + 10;
	}

	static int contentHeight(Viewport view, int count) {
		return backY(view, count) - cardY(view, 0) + PAD + BUTTON_HEIGHT + 8;
	}
}
