package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The first menu page: the farm categories, then My Schematics and Options. The category of the current farm is highlighted. */
public final class SelectionPage implements TablePage {
	private static final Category[] CATEGORIES = Category.values();

	private final PageContext ui;

	public SelectionPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		Category current = Category.of(ui.farm()).orElse(null);
		for (int index = 0; index < CATEGORIES.length; index++) {
			Category category = CATEGORIES[index];
			ui.card(context, category.icon().getDefaultStack(), category.title(), count(category), ui.view().cardY(index), mouseX, mouseY, category == current, true);
		}
		ui.card(context, new ItemStack(Items.BOOK), Text.translatable("gui.the_dream_table.schemes"), schemesY(), mouseX, mouseY, false, true);
		ui.card(context, new ItemStack(Items.REPEATER), Text.translatable("gui.the_dream_table.options"), optionsY(), mouseX, mouseY, false, true);
	}

	private static Text count(Category category) {
		int farms = category.groups().size();
		return farms == 1 ? Text.translatable("gui.the_dream_table.farm_count_one") : Text.translatable("gui.the_dream_table.farm_count", farms);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < CATEGORIES.length; index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				ui.go(DreamTableScreenHandler.PAGE_CATEGORY + index, DreamTableScreenHandler.BUTTON_CATEGORY + index);
				return true;
			}
		}
		if (ui.cardHit(mouseX, mouseY, schemesY())) {
			ui.go(DreamTableScreenHandler.PAGE_SCHEMES, DreamTableScreenHandler.BUTTON_SCHEMES);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, optionsY())) {
			ui.go(DreamTableScreenHandler.PAGE_OPTIONS, DreamTableScreenHandler.BUTTON_OPTIONS);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = CATEGORIES.length + 2;
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.choose");
	}

	private int schemesY() {
		return ui.view().cardY(CATEGORIES.length);
	}

	private int optionsY() {
		return ui.view().cardY(CATEGORIES.length + 1);
	}
}
