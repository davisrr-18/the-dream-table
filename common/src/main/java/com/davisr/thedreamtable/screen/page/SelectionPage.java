package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

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
			ui.cardAt("category." + category.name().toLowerCase(Locale.ROOT), context, category.icon().getDefaultStack(), category.title(), count(category),
					ui.view().gridCardX(index), ui.view().gridCardY(index), mouseX, mouseY, category == current, true);
		}
	}

	private static Text count(Category category) {
		int farms = category.farms().size();
		return farms == 1 ? Text.translatable("gui.the_dream_table.farm_count_one") : Text.translatable("gui.the_dream_table.farm_count", farms);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < CATEGORIES.length; index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(index), ui.view().gridCardY(index))) {
				ui.go(DreamTableScreenHandler.PAGE_CATEGORY + index, DreamTableScreenHandler.BUTTON_CATEGORY + index);
				return true;
			}
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = CATEGORIES.length;
		return ui.view().gridContentHeight(count);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.choose");
	}

}
