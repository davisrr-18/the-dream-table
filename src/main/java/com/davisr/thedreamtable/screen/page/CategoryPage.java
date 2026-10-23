package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.farm.FarmOptions.Group;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * The farm kinds of one category, such as Sugar Cane, Bamboo and Crops. A kind with several sizes opens {@link GroupPage};
 * a kind with one size is shown and chosen right here, so it takes no extra click.
 */
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
		List<Group> groups = category().groups();
		Group current = Group.of(ui.farm()).orElse(null);
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < groups.size(); index++) {
			Group group = groups.get(index);
			int y = ui.view().cardY(index);
			if (group.sizes().size() == 1) {
				FarmOption only = group.sizes().get(0);
				boolean selected = only.id().equals(ui.farm());
				FarmCard.draw(ui, context, only, y, mouseX, mouseY, selected, !locked || selected);
			} else {
				Text sizes = Text.translatable("gui.the_dream_table.size_count", group.sizes().size());
				ui.card(context, group.icon().getDefaultStack(), group.title(), sizes, y, mouseX, mouseY, group == current, true);
			}
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<Group> groups = category().groups();
		for (int index = 0; index < groups.size(); index++) {
			if (!ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				continue;
			}
			Group group = groups.get(index);
			if (group.sizes().size() == 1) {
				GroupPage.choose(ui, group.sizes().get(0));
			} else {
				ui.go(DreamTableScreenHandler.PAGE_GROUP + group.ordinal(), DreamTableScreenHandler.BUTTON_GROUP + group.ordinal());
			}
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_CATEGORY_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = category().groups().size();
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return category().title();
	}

	private int backY() {
		return ui.view().cardY(category().groups().size() - 1) + CARD_HEIGHT + 6;
	}
}
