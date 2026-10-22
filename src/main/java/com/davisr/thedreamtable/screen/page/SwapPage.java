package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Replacements for one material, filtered by the search box. Button ids carry the index in the full list. */
public final class SwapPage implements TablePage {
	private final PageContext ui;
	private final SearchBox search;

	public SwapPage(PageContext ui, SearchBox search) {
		this.ui = ui;
		this.search = search;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		int material = ui.handler().getSwapMaterial();
		List<Item> options = ui.handler().swapOptions(material);
		List<Integer> shown = search.filter(options);
		Item current = ui.handler().materialItem(material);
		for (int position = 0; position < shown.size(); position++) {
			int y = ui.view().cardY(position);
			if (!ui.view().cardVisible(y)) {
				continue;
			}
			Item option = options.get(shown.get(position));
			ui.card(context, new ItemStack(option), option.getName(), y, mouseX, mouseY, option == current, true);
		}
		if (shown.isEmpty()) {
			context.drawText(ui.painter().text(), Text.translatable("gui.the_dream_table.search_empty"), ui.view().cardX(), ui.view().cardY(0), HINT, false);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), ui.view().listBackY(shown.size()), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<Integer> shown = shownOptions();
		for (int position = 0; position < shown.size(); position++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(position))) {
				ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_SWAP_PICK + shown.get(position));
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, ui.view().listBackY(shown.size()))) {
			ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_SWAP_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return Viewport.listContentHeight(shownOptions().size());
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.swap");
	}

	private List<Integer> shownOptions() {
		return search.filter(ui.handler().swapOptions(ui.handler().getSwapMaterial()));
	}
}
