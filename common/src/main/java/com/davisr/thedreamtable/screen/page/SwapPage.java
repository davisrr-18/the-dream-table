package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Replacements for one material, filtered by the search box. Button ids carry the index in the full list. */
public final class SwapPage implements TablePage {
	private final PageContext ui;
	private final SearchBox search;
	/** The options for the open material and the ones the search keeps, made once per change instead of per call. */
	private String cachedFarm;
	private int cachedMaterial = -1;
	private String cachedQuery;
	private List<Item> cachedOptions = List.of();
	private List<Integer> cachedShown = List.of();

	public SwapPage(PageContext ui, SearchBox search) {
		this.ui = ui;
		this.search = search;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		int material = ui.handler().getSwapMaterial();
		List<Integer> shown = shownOptions();
		List<Item> options = cachedOptions;
		Item current = ui.handler().materialItem(material);
		for (int position = 0; position < shown.size(); position++) {
			int x = cardX(ui.view(), position);
			int y = cardY(ui.view(), position);
			Item option = options.get(shown.get(position));
			ui.cardAt("swap." + Registries.ITEM.getId(option), context, new ItemStack(option), option.getName(), x, y, mouseX, mouseY, option == current, true);
		}
		if (shown.isEmpty()) {
			ui.collectionNote(context, Text.translatable("gui.the_dream_table.search_empty"), cardY(ui.view(), 0), HINT);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(ui.view(), shown.size()), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<Integer> shown = shownOptions();
		for (int position = 0; position < shown.size(); position++) {
			if (ui.cardHit(mouseX, mouseY, cardX(ui.view(), position), cardY(ui.view(), position))) {
				ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_SWAP_PICK + shown.get(position));
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY(ui.view(), shown.size()))) {
			ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_SWAP_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return contentHeight(ui.view(), shownOptions().size());
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.swap");
	}

	private List<Integer> shownOptions() {
		String farm = ui.farm();
		int material = ui.handler().getSwapMaterial();
		String query = search.text();
		if (!farm.equals(cachedFarm) || material != cachedMaterial || !query.equals(cachedQuery)) {
			cachedOptions = ui.handler().swapOptions(material);
			cachedShown = search.filter(cachedOptions);
			cachedFarm = farm;
			cachedMaterial = material;
			cachedQuery = query;
		}
		return cachedShown;
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
