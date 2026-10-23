package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The farms of one category, each drawn as a {@link FarmCard} with its block count and how long it would take. */
public final class CategoryPage implements TablePage {
	private final PageContext ui;

	public CategoryPage(PageContext ui) {
		this.ui = ui;
	}

	public static boolean isCategoryPage(int page) {
		return Category.byIndex(page - DreamTableScreenHandler.PAGE_CATEGORY).isPresent();
	}

	private Category category() {
		return Category.byIndex(ui.handler().getPage() - DreamTableScreenHandler.PAGE_CATEGORY).orElse(Category.PLANTS);
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> farms = category().farms();
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < farms.size(); index++) {
			FarmOption option = farms.get(index);
			boolean selected = option.id().equals(ui.farm());
			FarmCard.draw(ui, context, option, ui.view().cardY(index), mouseX, mouseY, selected, !locked || selected);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<FarmOption> farms = category().farms();
		for (int index = 0; index < farms.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				FarmOption option = farms.get(index);
				if (!option.id().equals(ui.farm()) && ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
					return true;
				}
				ui.go(option.id(), DreamTableScreenHandler.PAGE_WORK, FarmOptions.ALL.indexOf(option));
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_CATEGORY_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = category().farms().size();
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return category().title();
	}

	private int backY() {
		return ui.view().cardY(category().farms().size() - 1) + CARD_HEIGHT + 6;
	}
}
